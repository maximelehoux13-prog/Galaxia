package com.gtnewhorizons.galaxia.outpost.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.event.world.WorldEvent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsTask;
import com.gtnewhorizons.galaxia.outpost.logistics.OutpostLogisticsEngine;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingMode;
import com.gtnewhorizons.galaxia.outpost.module.BigHammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.HammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.MinerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.OutpostModuleData;
import com.gtnewhorizons.galaxia.outpost.module.PowerModuleData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Handles JSON persistence for the outpost system.
 *
 * <h3>Storage layout</h3>
 * <pre>
 * world/galaxiadata/
 *   [TeamUUID]/
 *     outposts.json   ← AutomatedOutpostState list (buffer + config + modules)
 *   _tasks.json       ← global in-flight LogisticsTask list
 * </pre>
 *
 * <p>Data lives in RAM at all times. JSON files are only written on {@link WorldEvent.Save}
 * and read on world load (via {@link #onWorldLoad(WorldEvent.Load)}).
 *
 * <h3>Polymorphic module data</h3>
 * A custom {@link OutpostModuleDataAdapter} serializes {@link OutpostModuleData} with a
 * {@code "type"} discriminator field, enabling clean round-trip serialization.
 */
public final class OutpostPersistenceManager {

    private static final String DATA_DIR = "galaxiadata";
    private static final String OUTPOSTS_FILE = "outposts.json";
    private static final String TASKS_FILE = "_tasks.json";

    private final Gson gson;
    /** Root save directory for the current world; set on world load. */
    private File worldSaveDir;

    public OutpostPersistenceManager() {
        gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(OutpostModuleData.class, new OutpostModuleDataAdapter())
            .setPrettyPrinting()
            .create();
    }

    // -------------------------------------------------------------------------
    // FML event hooks
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.world instanceof WorldServer)) return;
        if (event.world.provider.dimensionId != 0) return; // overworld only
        ISaveHandler saveHandler = event.world.getSaveHandler();
        worldSaveDir = saveHandler.getWorldDirectory();
        OutpostDataStore.get()
            .clear();
        OutpostLogisticsEngine.get()
            .activeTasksInternal()
            .clear();
        loadAll();
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (!(event.world instanceof WorldServer)) return;
        if (event.world.provider.dimensionId != 0) return;
        if (worldSaveDir == null) return;
        saveAll();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!(event.world instanceof WorldServer)) return;
        if (event.world.provider.dimensionId != 0) return;
        OutpostDataStore.get()
            .clear();
        OutpostLogisticsEngine.get()
            .activeTasksInternal()
            .clear();
        worldSaveDir = null;
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    private void loadAll() {
        File galaxiaRoot = new File(worldSaveDir, DATA_DIR);
        if (!galaxiaRoot.exists()) return;

        // Load per-team outpost files.
        File[] teamDirs = galaxiaRoot.listFiles(File::isDirectory);
        if (teamDirs != null) {
            for (File teamDir : teamDirs) {
                try {
                    UUID teamId = UUID.fromString(teamDir.getName());
                    loadOutposts(teamId, new File(teamDir, OUTPOSTS_FILE));
                } catch (IllegalArgumentException e) {
                    // Directory name is not a UUID – skip silently.
                }
            }
        }

        // Load global task file.
        loadTasks(new File(galaxiaRoot, TASKS_FILE));
    }

    private void loadOutposts(UUID teamId, File file) {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<OutpostJson>>() {}.getType();
            List<OutpostJson> list = gson.fromJson(reader, listType);
            if (list == null) return;
            for (OutpostJson json : list) {
                AutomatedOutpostState state = new AutomatedOutpostState(
                    json.assetId,
                    teamId,
                    json.celestialBodyId,
                    json.systemId);
                state.setEnergyStored(json.energyStored);
                // Modules.
                if (json.modules != null) {
                    for (ModuleJson mj : json.modules) {
                        OutpostModuleKind kind = OutpostModuleKind.valueOf(mj.kind);
                        OutpostModuleData data = gson.fromJson(mj.data, OutpostModuleData.class);
                        AutomatedOutpostModule module = new AutomatedOutpostModule(kind, data);
                        if (mj.status != null) {
                            module.setStatus(AutomatedOutpostModule.Status.valueOf(mj.status));
                        }
                        module.setConstructionProgress(mj.constructionProgress);
                        module.cooldownTicks = mj.cooldownTicks;
                        module.energyBuffer = mj.energyBuffer;
                        module.clearConsumedResources();
                        if (mj.consumedResources != null) {
                            for (Map.Entry<String, Integer> e : mj.consumedResources.entrySet()) {
                                ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                                if (key != null) {
                                    module.getConsumedResources().put(key, e.getValue());
                                }
                            }
                        }
                        state.addModule(module);
                    }
                }
                // Inventory.
                if (json.buffer != null) {
                    Map<ItemStackWrapper, Long> bufferSnapshot = new LinkedHashMap<>();
                    for (Map.Entry<String, Long> e : json.buffer.entrySet()) {
                        ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                        if (key != null) {
                            bufferSnapshot.put(key, e.getValue());
                        }
                    }
                    state.inventory.loadFromSnapshot(bufferSnapshot);
                }
                // Logistics config.
                if (json.logisticsConfig != null) {
                    Map<ItemStackWrapper, LogisticsResourceConfig> cfgSnapshot = new LinkedHashMap<>();
                    for (Map.Entry<String, LogisticsConfigJson> e : json.logisticsConfig.entrySet()) {
                        ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                        if (key != null) {
                            LogisticsConfigJson cj = e.getValue();
                            cfgSnapshot.put(
                                key,
                                new LogisticsResourceConfig(cj.minReserve, cj.orderSize, cj.isImportEnabled,
                                    cj.isSupplyEnabled));
                        }
                    }
                    state.logisticsConfig.loadFromSnapshot(cfgSnapshot);
                }
                OutpostDataStore.get()
                    .put(state);
            }
        } catch (IOException | JsonParseException e) {
            Galaxia.LOG.error("[Logistics] Failed to load outposts from {}: {}", file, e.getMessage());
        }
    }

    private void loadTasks(File file) {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<TaskJson>>() {}.getType();
            List<TaskJson> list = gson.fromJson(reader, listType);
            if (list == null) return;
            List<LogisticsTask> tasks = OutpostLogisticsEngine.get()
                .activeTasksInternal();
            for (TaskJson tj : list) {
                ItemStackWrapper resource = ItemStackWrapper.fromKey(tj.resourceId);
                if (resource != null) {
                    tasks.add(
                        new LogisticsTask(
                            tj.taskId,
                            tj.fromAssetId,
                            tj.toAssetId,
                            resource,
                            tj.amount,
                            tj.remainingTicks,
                            tj.transportKind,
                            tj.fromBodyId != null ? tj.fromBodyId : "",
                            tj.toBodyId != null ? tj.toBodyId : "",
                            tj.departureOrbitalTime,
                            tj.tofOrbitalSeconds));
                }
            }
        } catch (IOException | JsonParseException e) {
            Galaxia.LOG.error("[Logistics] Failed to load tasks from {}: {}", file, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    private void saveAll() {
        // Group outposts by team for folder structure.
        Map<UUID, List<AutomatedOutpostState>> byTeam = new LinkedHashMap<>();
        for (AutomatedOutpostState state : OutpostDataStore.get()
            .allOutposts()) {
            byTeam.computeIfAbsent(state.teamId, k -> new ArrayList<>())
                .add(state);
        }

        File galaxiaRoot = new File(worldSaveDir, DATA_DIR);
        galaxiaRoot.mkdirs();

        for (Map.Entry<UUID, List<AutomatedOutpostState>> entry : byTeam.entrySet()) {
            File teamDir = new File(galaxiaRoot, entry.getKey()
                .toString());
            teamDir.mkdirs();
            saveOutposts(entry.getValue(), new File(teamDir, OUTPOSTS_FILE));
        }

        saveTasks(new File(galaxiaRoot, TASKS_FILE));
    }

    private void saveOutposts(List<AutomatedOutpostState> states, File file) {
        List<OutpostJson> list = new ArrayList<>();
        for (AutomatedOutpostState state : states) {
            OutpostJson j = new OutpostJson();
            j.assetId = state.assetId;
            j.celestialBodyId = state.celestialBodyId;
            j.systemId = state.systemId;
            j.energyStored = state.getEnergyStored();
            j.modules = new ArrayList<>();
            for (AutomatedOutpostModule m : state.modules()) {
                ModuleJson mj = new ModuleJson();
                mj.kind = m.kind.name();
                mj.status = m.getStatus().name();
                mj.constructionProgress = m.getConstructionProgress();
                mj.cooldownTicks = m.cooldownTicks;
                mj.energyBuffer = m.energyBuffer;
                mj.data = gson.toJsonTree(m.getData());
                mj.consumedResources = new LinkedHashMap<>();
                for (Map.Entry<ItemStackWrapper, Integer> e : m.getConsumedResources().entrySet()) {
                    mj.consumedResources.put(e.getKey().toKey(), e.getValue());
                }
                j.modules.add(mj);
            }
            j.buffer = new LinkedHashMap<>();
            for (Map.Entry<ItemStackWrapper, Long> e : state.inventory.snapshot()
                .entrySet()) {
                j.buffer.put(e.getKey()
                    .toKey(), e.getValue());
            }
            j.logisticsConfig = new LinkedHashMap<>();
            for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> e : state.logisticsConfig.snapshot()
                .entrySet()) {
                LogisticsConfigJson cj = new LogisticsConfigJson();
                cj.minReserve = e.getValue()
                    .minReserve();
                cj.orderSize = e.getValue()
                    .orderSize();
                cj.isImportEnabled = e.getValue()
                    .isImportEnabled();
                cj.isSupplyEnabled = e.getValue()
                    .isSupplyEnabled();
                j.logisticsConfig.put(e.getKey()
                    .toKey(), cj);
            }
            list.add(j);
        }
        writeJson(file, list);
    }

    private void saveTasks(File file) {
        List<TaskJson> list = new ArrayList<>();
        for (LogisticsTask task : OutpostLogisticsEngine.get()
            .activeTasksInternal()) {
            TaskJson tj = new TaskJson();
            tj.taskId = task.taskId();
            tj.fromAssetId = task.fromAssetId();
            tj.toAssetId = task.toAssetId();
            tj.resourceId = task.resourceId()
                .toKey();
            tj.amount = task.amount();
            tj.remainingTicks = task.remainingTicks();
            tj.transportKind = task.transportKind();
            tj.fromBodyId = task.fromBodyId();
            tj.toBodyId = task.toBodyId();
            tj.departureOrbitalTime = task.departureOrbitalTime();
            tj.tofOrbitalSeconds = task.tofOrbitalSeconds();
            list.add(tj);
        }
        writeJson(file, list);
    }

    private void writeJson(File file, Object value) {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(value, writer);
        } catch (IOException e) {
            Galaxia.LOG.error("[Logistics] Failed to write {}: {}", file, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // GSON intermediate DTOs (package-private)
    // -------------------------------------------------------------------------

    static final class OutpostJson {

        String assetId;
        String celestialBodyId;
        String systemId;
        long energyStored;
        List<ModuleJson> modules;
        Map<String, Long> buffer;
        Map<String, LogisticsConfigJson> logisticsConfig;
    }

    static final class ModuleJson {

        String kind;
        String status;
        float constructionProgress;
        int cooldownTicks;
        long energyBuffer;
        JsonElement data;
        Map<String, Integer> consumedResources;
    }

    static final class LogisticsConfigJson {

        int minReserve;
        int orderSize;
        boolean isImportEnabled;
        boolean isSupplyEnabled;
    }

    static final class TaskJson {

        String taskId;
        String fromAssetId;
        String toAssetId;
        String resourceId;
        long amount;
        int remainingTicks;
        String transportKind;
        // Trajectory metadata (optional – absent in legacy saves)
        String fromBodyId;
        String toBodyId;
        double departureOrbitalTime;
        double tofOrbitalSeconds;
    }

    // -------------------------------------------------------------------------
    // OutpostModuleData TypeAdapter (polymorphic serialization)
    // -------------------------------------------------------------------------

    /**
     * GSON adapter that serializes {@link OutpostModuleData} as:
     * <pre>{"type":"HAMMER"}</pre>
     * and deserializes by reading the {@code "type"} field to select the concrete record type.
     */
    private static final class OutpostModuleDataAdapter
        implements JsonSerializer<OutpostModuleData>, JsonDeserializer<OutpostModuleData> {

        private static final String TYPE_FIELD = "type";
        private static final Map<String, Class<? extends OutpostModuleData>> TYPE_MAP = new HashMap<>();
        private static final Gson PURE_GSON = new GsonBuilder().create();

        static {
            TYPE_MAP.put("HAMMER", HammerModuleData.class);
            TYPE_MAP.put("BIG_HAMMER", BigHammerModuleData.class);
            TYPE_MAP.put("MINER", MinerModuleData.class);
            TYPE_MAP.put("POWER", PowerModuleData.class);
        }

        @Override
        public JsonElement serialize(OutpostModuleData src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty(TYPE_FIELD, resolveTypeName(src.getClass()));
            // Use PURE_GSON to avoid recursion through the hierarchy adapter
            JsonElement fields = PURE_GSON.toJsonTree(src, src.getClass());
            if (fields.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : fields.getAsJsonObject()
                    .entrySet()) {
                    obj.add(entry.getKey(), entry.getValue());
                }
            }
            return obj;
        }

        @Override
        public OutpostModuleData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String type = obj.get(TYPE_FIELD)
                .getAsString();
            Class<? extends OutpostModuleData> clazz = TYPE_MAP.get(type);
            if (clazz == null) throw new JsonParseException("Unknown OutpostModuleData type: " + type);
            // Use PURE_GSON to avoid recursion through the hierarchy adapter
            return PURE_GSON.fromJson(obj, clazz);
        }

        private static String resolveTypeName(Class<?> clazz) {
            for (Map.Entry<String, Class<? extends OutpostModuleData>> e : TYPE_MAP.entrySet()) {
                if (e.getValue() == clazz) return e.getKey();
            }
            throw new IllegalArgumentException("Unregistered OutpostModuleData class: " + clazz.getName());
        }
    }
}
