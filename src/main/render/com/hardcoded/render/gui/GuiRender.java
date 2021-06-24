package com.hardcoded.render.gui;

import org.lwjgl.opengl.GL11;

import com.hardcoded.api.IResource;
import com.hardcoded.mc.general.world.IBlockData;
import com.hardcoded.render.gui.GuiListener.GuiEvent.GuiKeyEvent;
import com.hardcoded.render.gui.GuiListener.GuiEvent.GuiMouseEvent;
import com.hardcoded.render.gui.components.GuiBlockMenu;
import com.hardcoded.render.gui.components.GuiToolList;

public class GuiRender extends IResource {
	private GuiToolList tools;
	private GuiPanel panel;
	
	public IBlockData selectedBlock;
	
	public GuiRender() {
		
	}
	
	@Override
	public void init() {
		try {
			GuiLabel.createTextAtlas();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		panel = new GuiPanel();
		
		GuiBlockMenu blockMenu = new GuiBlockMenu(this);
		blockMenu.setBounds(72, 18, 500, 500);
		panel.add(blockMenu);
		
		tools = new GuiToolList(this, 0, 18);
		tools.setSize(72, 72 * 8);
		panel.add(tools);
	}
	
	public void render() {
		if(panel == null) return;

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glColor4f(1, 1, 1, 1);
		
		panel.render();
		
		GL11.glDisable(GL11.GL_BLEND);
	}
	
	public GuiListener processMouseEvent(GuiMouseEvent event) {
		if(event.getY() < 18 || panel == null) return null;
		
		return panel.processMouseEvent(event, true);
	}

	public void processKeyEvent(GuiKeyEvent event) {
		if(panel == null) return;
		panel.processKeyEvent(event);
	}
}
