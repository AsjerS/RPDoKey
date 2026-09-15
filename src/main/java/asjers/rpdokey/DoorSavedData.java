package asjers.rpdokey;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

public class DoorSavedData extends SavedData {
    private final Map<BlockPos, String> lockedDoors = new HashMap<>();

    public DoorSavedData() {}

    public DoorSavedData(Map<BlockPos, String> map) {
        this.lockedDoors.putAll(map);
    }

    public static final Codec<DoorSavedData> CODEC = Codec.unboundedMap(
        Codec.STRING.xmap(
            s -> BlockPos.of(Long.parseLong(s)),
            pos -> String.valueOf(pos.asLong())
        ),
        Codec.STRING
    ).xmap(DoorSavedData::new, data -> data.lockedDoors);

    public static final SavedDataType<DoorSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("rpdokey", "doors"),
        DoorSavedData::new,
        CODEC,
        null
    );

    public static DoorSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isLocked(BlockPos pos) {
        return lockedDoors.containsKey(pos);
    }

    public String getKey(BlockPos pos) {
        return lockedDoors.get(pos);
    }

    public void setLock(BlockPos pos, String keyName) {
        lockedDoors.put(pos, keyName);
        this.setDirty();
    }

    public void removeLock(BlockPos pos) {
        lockedDoors.remove(pos);
        this.setDirty();
    }
}