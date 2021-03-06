package com.therandomlabs.randompatches.patch;

import com.therandomlabs.randompatches.core.Patch;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

//Fix taken from
//https://github.com/gnembon/carpetmod112/blob/staging/patches/net/minecraft/server/management/
//PlayerInteractionManager.java.patch
//Thanks, gnembon!
public final class PlayerInteractionManagerPatch extends Patch {
	/**
	 * Expected result:
	 * int i = (int) (f * 10.0F); //(This is the last ISTORE in the method)
	 * PlayerInteractionManagerPatch.sendBlockChangePacket(this, destroyPos);
	 * world.sendBlockBreakProgress(player.getEntityId(), pos, i);
	 */
	@Override
	public boolean apply(ClassNode node) {
		final InsnList instructions = findInstructions(node, "onBlockClicked", "func_180784_a");
		AbstractInsnNode storeProgress = null;

		for (int i = instructions.size() - 1; i >= 0; i--) {
			storeProgress = instructions.get(i);

			if (storeProgress.getOpcode() == Opcodes.ISTORE) {
				break;
			}

			storeProgress = null;
		}

		final InsnList newInstructions = new InsnList();

		//Get PlayerInteractionManager (this)
		newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 0));

		//Get PlayerInteractionManager (this)
		newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 0));

		//Get PlayerInteractionManager#destroyPos
		newInstructions.add(new FieldInsnNode(
				Opcodes.GETFIELD,
				node.name,
				getName("destroyPos", "field_180240_f"),
				"Lnet/minecraft/util/math/BlockPos;"
		));

		//Call PlayerInteractionManagerHook#sendBlockChangePacket
		newInstructions.add(new MethodInsnNode(
				Opcodes.INVOKESTATIC,
				hookClass,
				"sendBlockChangePacket",
				"(L" + node.name + ";Lnet/minecraft/util/math/BlockPos;)V",
				false
		));

		instructions.insert(storeProgress, newInstructions);

		return true;
	}
}
