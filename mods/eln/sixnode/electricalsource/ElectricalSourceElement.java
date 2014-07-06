package mods.eln.sixnode.electricalsource;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import javax.swing.text.MaskFormatter;

import cpw.mods.fml.common.FMLCommonHandler;


import mods.eln.Eln;
import mods.eln.generic.GenericItemUsingDamageDescriptor;
import mods.eln.item.BrushDescriptor;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeElectricalLoad;
import mods.eln.node.NodeThermalLoad;
import mods.eln.node.SixNode;
import mods.eln.node.SixNodeDescriptor;
import mods.eln.node.SixNodeElement;
import mods.eln.sim.ElectricalLoad;
import mods.eln.sim.IProcess;
import mods.eln.sim.ThermalLoad;
import mods.eln.sim.mna.component.VoltageSource;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

public class ElectricalSourceElement extends SixNodeElement {

	public ElectricalSourceElement(SixNode sixNode, Direction side, SixNodeDescriptor descriptor) {
		super(sixNode, side, descriptor);
		electricalLoadList.add(electricalLoad);
		electricalComponentList.add(voltageSource);
	}

	NodeElectricalLoad electricalLoad = new NodeElectricalLoad("electricalLoad");
	VoltageSource voltageSource = new VoltageSource("voltSrc",electricalLoad, null);

	public static final int setVoltageId = 1;
	
	int color = 0;
	int colorCare = 0;

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		byte b = nbt.getByte( "color");
		color = b & 0xF;
		colorCare = (b >> 4) & 1;
		
		voltageSource.setU(nbt.getDouble("voltage"));
	}
	
	public static boolean canBePlacedOnSide(Direction side, int type) {
		return true;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
 		super.writeToNBT(nbt);
		nbt.setByte("color", (byte)(color + (colorCare << 4)));
		
		nbt.setDouble("voltage",voltageSource.getU());
	}

	@Override
	public ElectricalLoad getElectricalLoad(LRDU lrdu) {
		return electricalLoad;
	}

	@Override
	public ThermalLoad getThermalLoad(LRDU lrdu) {
		return null;
	}

	@Override
	public int getConnectionMask(LRDU lrdu) {
		return NodeBase.maskElectricalPower + (color << NodeBase.maskColorShift) + (colorCare << NodeBase.maskColorCareShift);
	}

	@Override
	public String multiMeterString() {
		return Utils.plotUIP(electricalLoad.getU(), voltageSource.getI());
	}
	
	@Override
	public String thermoMeterString() {
		return "";
	}

	@Override
	public void networkSerialize(DataOutputStream stream) {
		super.networkSerialize(stream);
		try {
			stream.writeByte( (color << 4));
	    	stream.writeFloat((float) voltageSource.getU());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void initialize() {

		electricalLoad.setRs(0.000001);
	}

	@Override
	public boolean onBlockActivated(EntityPlayer entityPlayer, Direction side, float vx, float vy, float vz) {
		ItemStack currentItemStack = entityPlayer.getCurrentEquippedItem();
		if(Utils.isPlayerUsingWrench(entityPlayer)) {
			colorCare = colorCare ^ 1;
			Utils.addChatMessage(entityPlayer,"Wire color care " + colorCare);
			sixNode.reconnect();
		}
		else if(currentItemStack != null) {
			Item item = currentItemStack.getItem();

			GenericItemUsingDamageDescriptor gen = BrushDescriptor.getDescriptor(currentItemStack);
			if(gen != null && gen instanceof BrushDescriptor) {
				BrushDescriptor brush = (BrushDescriptor) gen;
				int brushColor = brush.getColor(currentItemStack);
				if(brushColor != color) {
					if(brush.use(currentItemStack)) {
						color = brushColor;
						sixNode.reconnect();
					}
					else {
						Utils.addChatMessage(entityPlayer,"Brush is empty");
					}
				}
			}
		}
		return false;
	}

	@Override
	public void networkUnserialize(DataInputStream stream) {
		super.networkUnserialize(stream);
		try {
			switch(stream.readByte()) {
			case setVoltageId:
				voltageSource.setU(stream.readFloat());
				needPublish();
				break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean hasGui() {
		return true;
	}
}