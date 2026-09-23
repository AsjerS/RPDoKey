package asjers.rpdokey;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class RPDoKey implements ModInitializer {
	public static ModConfig CONFIG;

	@Override
	public void onInitialize() {
		CONFIG = ModConfig.load();

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (
				level.isClientSide()
				|| hand != InteractionHand.MAIN_HAND
				|| !(player instanceof ServerPlayer serverPlayer)
			) {
				return InteractionResult.PASS;
			}

			BlockPos pos = hitResult.getBlockPos();
			BlockState state = level.getBlockState(pos);

			if (isDoor(state)) {
				ServerLevel serverLevel = (ServerLevel) level;

				if (isOp(serverPlayer, serverLevel))
					return InteractionResult.PASS;

				BlockPos basePos = getBasePos(state, pos);
				DoorSavedData data = DoorSavedData.get(serverLevel);

				if (data.isLocked(basePos)) {
					String requiredKey = data.getKey(basePos);
					ItemStack heldItem = serverPlayer.getMainHandItem();

					if (heldItem.getHoverName().getString().equals(requiredKey))
						return InteractionResult.PASS;

					serverPlayer.sendSystemMessage(
						Component.literal("This door is locked"), 
						true
					);
					return InteractionResult.FAIL;
				} else if (CONFIG.lockAllByDefault()) {
					serverPlayer.sendSystemMessage(
						Component.literal("This door is locked by default"), 
						true
					);
					return InteractionResult.FAIL;
				}
			}

			return InteractionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("rpdokey")
				.then(Commands.literal("lock")
					.then(Commands.argument("key", StringArgumentType.greedyString())
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							ServerLevel level = (ServerLevel) player.level();

							if (!isOp(player, level)) {
								player.sendSystemMessage(
									Component.literal("Only OPs can lock doors"),
									false
								);
								return 0;
							}

							BlockPos basePos = getTargetedDoor(player, level);
							if (basePos == null) return 0;

							String keyName = StringArgumentType.getString(ctx, "key");
							DoorSavedData.get(level).setLock(basePos, keyName);

							player.sendSystemMessage(Component.literal(
								"Door locked with key: " + keyName),
								false
							);
							return 1;
						})
					)
				)
				.then(Commands.literal("unlock")
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						ServerLevel level = (ServerLevel) player.level();

						if (!isOp(player, level)) {
							player.sendSystemMessage(
								Component.literal("Only OPs can unlock doors"),
								false
							);
							return 0;
						}

						BlockPos basePos = getTargetedDoor(player, level);
						if (basePos == null) return 0;

						DoorSavedData.get(level).removeLock(basePos);
						player.sendSystemMessage(
							Component.literal("Door lock removed"),
							false
						);
						return 1;
					})
				)
				.then(Commands.literal("info")
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						ServerLevel level = (ServerLevel) player.level();

						BlockPos basePos = getTargetedDoor(player, level);
						if (basePos == null) return 0;

						DoorSavedData data = DoorSavedData.get(level);

						if (data.isLocked(basePos)) {
							player.sendSystemMessage(
								Component.literal(
									"This door is locked with key: "
									+ data.getKey(basePos)
								),
								false
							);
						} else {
							player.sendSystemMessage(
								Component.literal("This door is not locked"),
								false
							);
						}
						return 1;
					})
				)
			);
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!level.isClientSide() && isDoor(state)) {
				BlockPos basePos = getBasePos(state, pos);
				DoorSavedData data = DoorSavedData.get((ServerLevel) level);
				if (data.isLocked(basePos)) data.removeLock(basePos);
			}
		});
	}

	private static boolean isDoor(BlockState state) {
		return state.getBlock() instanceof DoorBlock
			|| state.getBlock() instanceof TrapDoorBlock
			|| state.getBlock() instanceof FenceGateBlock;
	}

	private static boolean isOp(ServerPlayer player, ServerLevel level) {
		return level.getServer().getPlayerList().isOp(player.nameAndId());
	}

	private BlockPos getTargetedDoor(ServerPlayer player, ServerLevel level) {
		BlockHitResult hit = raycast(player);
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);

		if (
			hit.getType() != HitResult.Type.BLOCK
			|| !isDoor(state)
		) {
			player.sendSystemMessage(
				Component.literal("You are not looking at a door"),
				false
			);
			return null;
		}

		return getBasePos(state, pos);
	}

	private BlockPos getBasePos(BlockState state, BlockPos pos) {
		if (
			state.getBlock() instanceof DoorBlock
			&& state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
		) {
			return pos.below();
		}
		return pos;
	}

	private static BlockHitResult raycast(ServerPlayer player) {
		Vec3 eyePos = player.getEyePosition();
		return player.level().clip(new ClipContext(
			eyePos,
			eyePos.add(player.getViewVector(1.0F).scale(5.0)),
			ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE,
			player
		));
	}
}