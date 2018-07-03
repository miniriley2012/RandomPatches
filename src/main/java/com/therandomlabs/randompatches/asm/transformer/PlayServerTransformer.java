package com.therandomlabs.randompatches.asm.transformer;

import com.therandomlabs.randompatches.asm.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class PlayServerTransformer extends Transformer {
	public static final PlayServerTransformer INSTANCE = new PlayServerTransformer();

	public static final String LAST_PING_TIME = "field_194402_f";
	public static final String SEND_PACKET = getFieldName("sendPacket", "a");

	/* Expected result:

	final long KEEP_ALIVE_PACKET_INTERVAL = 15000L;
	final long READ_TIMEOUT = 90000L;

	long lastPingTime;
	long keepAliveID;
	boolean shouldDisconnect;

	void update() {
		final long currentTime = currentTimeMillis();

		if(currentTime - lastPingTime >= KEEP_ALIVE_PACKET_INTERVAL) {
			if(shouldDisconnect) {
				if(currentTime - lastPingTime >= READ_TIMEOUT) {
					disconnect(new TextComponentTranslation("disconnect.timeout"));
				}
			} else {
				shouldDisconnect = true;
				lastPingTime = currentTime;
				keepAliveID = currentTime;
				sendPacket(new SPacketKeepAlive(keepAliveID));
			}
		}
	} */

	@Override
	public boolean transform(ClassNode node) {
		final MethodNode methodNode = findUpdateMethod(node);

		LdcInsnNode keepAliveInterval = null;
		JumpInsnNode ifeq = null;
		MethodInsnNode sendPacket = null;

		for(int i = 0; i < methodNode.instructions.size(); i++) {
			final AbstractInsnNode instruction = methodNode.instructions.get(i);

			if(keepAliveInterval == null) {
				if(instruction.getType() == AbstractInsnNode.LDC_INSN) {
					keepAliveInterval = (LdcInsnNode) instruction;

					if(!((Long) 15000L).equals(keepAliveInterval.cst)) {
						keepAliveInterval = null;
					}
				}

				continue;
			}

			if(ifeq == null) {
				if(instruction.getOpcode() == Opcodes.IFEQ &&
						instruction.getPrevious().getOpcode() == Opcodes.GETFIELD) {
					ifeq = (JumpInsnNode) instruction;
				}

				continue;
			}

			if(sendPacket == null) {
				if(instruction.getOpcode() == Opcodes.INVOKEVIRTUAL) {
					sendPacket = (MethodInsnNode) instruction;

					if(SEND_PACKET.equals(sendPacket.name)) {
						break;
					}

					sendPacket = null;
				}
			}
		}

		if(sendPacket == null) {
			return false;
		}

		final FieldInsnNode getKeepAliveInterval = new FieldInsnNode(
				Opcodes.GETSTATIC,
				"com/therandomlabs/randompatches/RPStaticConfig",
				"keepAlivePacketIntervalMillis",
				"J"
		);

		methodNode.instructions.insert(keepAliveInterval, getKeepAliveInterval);
		methodNode.instructions.remove(keepAliveInterval);

		final LabelNode label = new LabelNode();

		final VarInsnNode loadCurrentTime = new VarInsnNode(Opcodes.LLOAD, 1);

		final VarInsnNode loadThis = new VarInsnNode(Opcodes.ALOAD, 0);
		final FieldInsnNode getPreviousTime = new FieldInsnNode(
				Opcodes.GETFIELD,
				"net/minecraft/network/NetHandlerPlayServer",
				LAST_PING_TIME,
				"J"
		);

		final InsnNode subtract = new InsnNode(Opcodes.LSUB);

		final FieldInsnNode getReadTimeoutMillis = new FieldInsnNode(
				Opcodes.GETSTATIC,
				"com/therandomlabs/randompatches/RPStaticConfig",
				"readTimeoutMillis",
				"J"
		);

		final InsnNode compare = new InsnNode(Opcodes.LCMP);
		final JumpInsnNode jumpIfNotLarger = new JumpInsnNode(Opcodes.IFLT, label);

		methodNode.instructions.insert(ifeq, loadCurrentTime);
		methodNode.instructions.insert(loadCurrentTime, loadThis);
		methodNode.instructions.insert(loadThis, getPreviousTime);
		methodNode.instructions.insert(getPreviousTime, subtract);
		methodNode.instructions.insert(subtract, getReadTimeoutMillis);
		methodNode.instructions.insert(getReadTimeoutMillis, compare);
		methodNode.instructions.insert(compare, jumpIfNotLarger);

		methodNode.instructions.insert(sendPacket, label);

		return true;
	}
}