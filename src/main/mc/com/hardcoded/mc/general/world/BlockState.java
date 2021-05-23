package com.hardcoded.mc.general.world;

import com.hardcoded.mc.general.files.Blocks;

class BlockState implements IBlockState {
	protected final String name;
	protected final int id;
	protected int rgb;
	
	public BlockState(String name) {
		this.name = name;
		
		// Temporary id creation
		this.id = name.hashCode();
	}
	
	protected BlockState setColor(int rgb) {
		this.rgb = rgb;
		return this;
	}
	
	@Override
	public int getBlockId() {
		return id;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public int getMapColor() {
		return rgb;
	}
	
	@Override
	public boolean isAir() {
		return id == Blocks.AIR.getBlockId()
			|| id == Blocks.CAVE_AIR.getBlockId()
			|| id == Blocks.VOID_AIR.getBlockId();
	}
	
	@Override
	public String toString() {
		return "BlockState[" + name + "]";
	}
}