package com.hardcoded.render;

import static org.lwjgl.opengl.GL11.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.Math;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import com.hardcoded.lwjgl.Camera;
import com.hardcoded.lwjgl.LwjglWindow;
import com.hardcoded.lwjgl.data.TextureAtlas;
import com.hardcoded.lwjgl.input.Input;
import com.hardcoded.lwjgl.shader.MeshShader;
import com.hardcoded.lwjgl.shader.ShadowShader;
import com.hardcoded.lwjgl.shadow.ShadowFrameBuffer;
import com.hardcoded.main.ProjectEdit;
import com.hardcoded.mc.general.Minecraft;
import com.hardcoded.mc.general.files.Blocks;
import com.hardcoded.mc.general.files.IChunk;
import com.hardcoded.mc.general.files.IRegion;
import com.hardcoded.mc.general.world.World;
import com.hardcoded.render.gui.GuiRender;
import com.hardcoded.render.utils.RenderUtil;
import com.hardcoded.utils.*;
import com.hardcoded.utils.FastModelJsonLoader.FaceType;
import com.hardcoded.utils.FastModelJsonLoader.TestXY;

public class LwjglRender {
	private static final Logger LOGGER = LogManager.getLogger(LwjglRender.class);
	
	public static final TextureAtlas atlas = new TextureAtlas();
	
	private final long window;
	private GuiRender gui;
	
	// Initialized from ProjectEdit.getInstance().getWorld()
	public World world;
	// Initialized from ProjectEdit.getInstance().getCamera()
	public Camera camera;
	
	private WorldRender world_render;
	public LwjglRender(long window, int width, int height) {
		this.window = window;
		
		camera = ProjectEdit.getInstance().getCamera();
		world_render = new WorldRender(this);
		gui = new GuiRender(this);
		
		setViewport(width, height);
		
		try {
			init();
		} catch(Exception e) {
			LOGGER.error("Failed to load", e);
			throw e;
		}
	}
	
	public void setViewport(int width, int height) {
		GL11.glViewport(0, 0, width, height);
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_PROJECTION_MATRIX);
		GL11.glOrtho(0, width, height, 0, 1, -1);
		GL11.glMatrixMode(GL11.GL_MODELVIEW_MATRIX);
	}
	
	private File version_jar;
	private VersionResourceReader reader;
	
	public ShadowShader shadowShader;
	public MeshShader meshShader;
	private void init() {
		Blocks.init();
		
		try {
			shadowShader = new ShadowShader();
			meshShader = new MeshShader();
			frameBuffer = new ShadowFrameBuffer(8192, 8192);
		} catch(Exception e) {
			throw e;
		}
		
		File[] files = Minecraft.getSaves();
		if(files.length > 0) {
			File save = files[4]; // 2
			
			save = Minecraft.getSave("nec");
			save = Minecraft.getSave("2Test_Lighting");
//			save = Minecraft.getSave("11BlockModels");
			// 4
			
			LOGGER.info("Loading savefile: '{}'", save);
			world = ProjectEdit.getInstance().loadWorld(save);
			
			version_jar = new File(Minecraft.getVersionFolder(world.getVersion()), world.getVersion() + ".jar");
			LOGGER.info("Version file: '{}'", version_jar);
			
			try {
				reader = new VersionResourceReader(version_jar);
				reader.loadBlocks();
				debug_id = atlas.addTexture("projectedit:debug_faces", ImageIO.read(LwjglRender.class.getResourceAsStream("/images/debug_faces.png")));
				atlas.compile();
			} catch(IOException e) {
				e.printStackTrace();
			}
		} else {
			LOGGER.warn("Could not find any savefiles");
		}
	}
	
	public void update() {
		GLFW.glfwSwapBuffers(window);
		GLFW.glfwPollEvents();
		camera.update();
	}
	
	public void cleanup() {
		world_render.cleanup();
	}
	
	private int last_mvp_x;
	private int last_mvp_z = -1000000;
	private int last_mvp_y;
	private int last_mvp_scale = 1;
	public ShadowFrameBuffer frameBuffer;
	
	private Vector3f camera_pos;
	private Matrix4f camera_view;
	private int debug_id;
	
	public void render() {
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glClearColor(0.3f, 0.3f, 0.3f, 1);
		
		Matrix4f projectionView = camera.getProjectionMatrix();
		
		// Get the current loaded world
		World world = ProjectEdit.getInstance().getWorld();
		
		if(Input.isKeyDown(GLFW.GLFW_KEY_U) || camera_pos == null) {
			camera_pos = camera.getPosition();
			camera_view = camera.getProjectionMatrix();
		}
		
		if(Input.pollKey(GLFW.GLFW_KEY_K)) {
			// Unload rendered chunks
			world_render.unloadCache();
			// Unload model cache
			FastModelJsonLoader.unloadCache();
			// Unload all textures
			atlas.dispose();
			// Reload models
			reader.loadBlocks();
			
			// Add debug texture
			try {
				debug_id = atlas.addTexture("projectedit:debug_faces", ImageIO.read(LwjglRender.class.getResourceAsStream("/images/debug_faces.png")));
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			// Recompile textures
			atlas.compile();
		}
		
		{
			int mvp_scale = (int)(Math.sqrt(Math.max(camera.y / 16 - 8, 0)));
			if(mvp_scale < 1) mvp_scale = 1;
			boolean updateShadowMap = false;
			int cmx = Math.floorDiv((int)camera.x, 16) * 16;
			int cmy = Math.floorDiv((int)camera.y, 16) * 16;
			int cmz = Math.floorDiv((int)camera.z, 16) * 16;
			if(last_mvp_x != cmx || last_mvp_z != cmz || last_mvp_y != cmy || last_mvp_scale != mvp_scale) {
				last_mvp_scale = mvp_scale;
				last_mvp_x = cmx;
				last_mvp_y = cmy;
				last_mvp_z = cmz;
				updateShadowMap = true;
			}
			
			Matrix4f mvpMatrix = MathUtils.getOrthoProjectionMatrix(512 * mvp_scale, 512 * mvp_scale, 400);
			
			{
				float step = (System.currentTimeMillis() % 72000L) / 4000.0f;
				step = 45;
				step = (int)(step);
				
				int x = last_mvp_x;
				int y = last_mvp_y;
				int z = last_mvp_z;
				
				float angle = (float)Math.toRadians(step);
				mvpMatrix.rotateLocalX(-(float)Math.PI / 2.0f);
				mvpMatrix.rotateZ(0.8f);
				mvpMatrix.rotateY(angle);
				mvpMatrix.translate(-x, -y, -z);
			}
			
			int radius = 8;
			
			GL11.glEnable(GL11.GL_DEPTH_TEST);
			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glEnable(GL11.GL_ALPHA_TEST);
			GL11.glAlphaFunc(GL11.GL_GREATER, 0);
			
			if(updateShadowMap) {
				shadowShader.bind();
				shadowShader.setMvpMatrix(mvpMatrix);
				frameBuffer.bindFrameBuffer();
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
				GL11.glEnable(GL11.GL_DEPTH_TEST);
//				world_render.renderWorld(world, camera, projectionView, radius);
				shadowShader.unbind();
				frameBuffer.unbindFrameBuffer();
			}
			
			GL13.glActiveTexture(GL13.GL_TEXTURE1);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, frameBuffer.getShadowMap());
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.getTextureId());
			
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
			{
				meshShader.bind();
				meshShader.setShadowMapSpace(MathUtils.getShadowSpaceMatrix(mvpMatrix));
				meshShader.setProjectionView(projectionView);
				world_render.renderWorld(world, camera, projectionView, radius);
				meshShader.unbind();
			}
			GL11.glDisable(GL11.GL_BLEND);
			
			atlas.unbind();
			
			if(Input.isKeyDown(GLFW.GLFW_KEY_O)) {
				camera.x = 135;
				camera.y = 331;
				camera.z = 67;
				camera.ry = 90;
				camera.rx = 180;
			}
			
			if(Input.isKeyDown(GLFW.GLFW_KEY_P)) {
				camera.x = 1648;
				camera.y = 68.26f;
				camera.z = -2434;
				camera.rx = 180;
				camera.ry = 10;
			}
			
			GL11.glDisable(GL11.GL_CULL_FACE);
			
			GL11.glPushMatrix();
			GL11.glLoadMatrixf(projectionView.get(new float[16]));
			drawLoadedChunks(world);
//			drawFrustumChunks(world, camera_view, camera_pos, radius);
			GL11.glPopMatrix();
			
			GL11.glPushMatrix();
			GL11.glLoadMatrixf(projectionView.get(new float[16]));
//				RenderUtil.drawFrustum(projViewTest, campos, width, height, 20, 100);
				
				{
					GL11.glDisable(GL11.GL_ALPHA_TEST);
					GL13.glActiveTexture(GL13.GL_TEXTURE0);
					GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.getTextureId());
					GL11.glColor4d(1, 1, 1, 1);
					GL11.glBegin(GL11.GL_TRIANGLE_FAN);
						GL11.glTexCoord2d(0, 0); GL11.glVertex3d(  0, 256, 0);
						GL11.glTexCoord2d(1, 0); GL11.glVertex3d(256, 256, 0);
						GL11.glTexCoord2d(1, 1); GL11.glVertex3d(256, 256, 256);
						GL11.glTexCoord2d(0, 1); GL11.glVertex3d(  0, 256, 256);
					GL11.glEnd();
				}
				
//				{
//					GL13.glActiveTexture(GL13.GL_TEXTURE0);
//					GL11.glBindTexture(GL11.GL_TEXTURE_2D, frameBuffer.getShadowMap());
//					GL11.glColor4d(1, 1, 1, 1);
//					GL11.glBegin(GL11.GL_TRIANGLE_FAN);
//						GL11.glTexCoord2d(0, 0); GL11.glVertex3d(  0, 300, 0);
//						GL11.glTexCoord2d(1, 0); GL11.glVertex3d(256, 300, 0);
//						GL11.glTexCoord2d(1, 1); GL11.glVertex3d(256, 300, 256);
//						GL11.glTexCoord2d(0, 1); GL11.glVertex3d(  0, 300, 256);
//					GL11.glEnd();
//				}
				
			{
				Vector3f from = new Vector3f(0, 0, 0);
				Vector3f to = new Vector3f(16, 16, 16);
				
				GL11.glBegin(GL11.GL_TRIANGLES);
				for(int y = 0; y < 360; y += 90) {
					for(int x = 0; x < 360; x += 90) {
						int idx = (x / 90) + (y / 90) * 4;
						
						Matrix4f mat = new Matrix4f()
							.translateLocal(-8, -8, -8)
							.rotateLocalX(MathUtils.toRadians(-x))
							.rotateLocalY(MathUtils.toRadians(-y))
							.translateLocal(8, 8, 8);
						
						for(FaceType face : FaceType.FACES) {
							FaceType frot = face.rotate(mat);
							float[] vertex = Maths.getModelVertexes(face, from, to);
							float[] color = FastModelRenderer.getFaceColor(frot);
							color = FastModelRenderer.getDebugFaceColor(face);
							
							float[] uv = Maths.getModelUv(face, atlas, debug_id, from, to);
							GL11.glColor3f(color[0], color[1], color[2]);
							for(int vi = 0, ui = 0; vi < vertex.length; vi += 3, ui += 2) {
								float xp = vertex[vi];
								float yp = vertex[vi + 1];
								float zp = vertex[vi + 2];
								
								Vector3f v = mat.transformPosition(new Vector3f(xp, yp, zp));
								xp = v.x;
								yp = v.y;
								zp = v.z;
								
								GL11.glTexCoord2f(uv[ui], uv[ui + 1]);
								GL11.glVertex3f(xp / 16.0f + idx * 2, yp / 16.0f + 100 + ((y / 90) & 1), zp / 16.0f);
							}
						}
					}
				}
				
				for(int y = 0; y < 360; y += 90) {
					for(int x = 0; x < 360; x += 90) {
						int idx = (x / 90) + (y / 90) * 4;
						
						Matrix4f mat = new Matrix4f()
							.translateLocal(-8, -8, -8)
							.rotateLocalX(MathUtils.toRadians(-x))
							.rotateLocalY(MathUtils.toRadians(-y))
							.translateLocal(8, 8, 8);

						TestXY testXY = TestXY.testGet(x, y);
						for(FaceType face : FaceType.FACES) {
							FaceType frot = face.rotate(mat);
							float[] vertex = Maths.getModelVertexes(face, from, to);
							float[] color = FastModelRenderer.getFaceColor(frot);
							color = FastModelRenderer.getDebugFaceColor(face);
							
							float[] uv = Maths.getModelUv(face, atlas, atlas.getImageId("minecraft:block/magenta_glazed_terracotta"), from, to);
							{
								Vector4f v_uv = Maths.generateUv(face, from, to);
//								if(x == 90 && y == 90) {
//									if(frot == FaceType.north) {
//										System.out.println(testXY);
//										uv = FastModelJsonLoader.rotateUvTest(v_uv, 270);
//									} else {
//										uv = FastModelJsonLoader.rotateUvTest(v_uv, testXY.getTextureRotation(frot));
//									}
//								} else {
//								}
								uv = FastModelJsonLoader.rotateUvTest(v_uv, testXY.getTextureRotation(frot));
								atlas.transformModelUv(atlas.getImageId("minecraft:block/magenta_glazed_terracotta"), uv);
							}
							
							GL11.glColor3f(color[0], color[1], color[2]);
							for(int vi = 0, ui = 0; vi < vertex.length; vi += 3, ui += 2) {
								float xp = vertex[vi];
								float yp = vertex[vi + 1];
								float zp = vertex[vi + 2];
								
								Vector3f v = mat.transformPosition(new Vector3f(xp, yp, zp));
								xp = v.x;
								yp = v.y;
								zp = v.z;
								
								GL11.glTexCoord2f(uv[ui], uv[ui + 1]);
								GL11.glVertex3f(xp / 16.0f + idx * 2, yp / 16.0f + 105 + ((y / 90) & 1), zp / 16.0f);
							}
						}
					}
				}
				GL11.glEnd();
			}
			GL11.glPopMatrix();
		}
		
		GL11.glDisable(GL_DEPTH_TEST);
		
		GL11.glPushMatrix();
		gui.render();
		GL11.glPopMatrix();
		GL11.glDisable(GL11.GL_ALPHA_TEST);
	}
	
	private int displayList;
	private boolean has_regions;
//	private TextureAtlas chunk_atlas = new TextureAtlas();
	void drawLoadedChunks(World world) {
		if(!has_regions) {
			File regions = new File(world.getFolder(), "region");
			if(!regions.isDirectory()) return;
			
			File[] list = regions.listFiles();
			if(list == null) return;
			
			List<IRegion> copy = new ArrayList<>();
			for(File file : list) {
				String[] part = file.getName().split("\\.");
				int x = Integer.valueOf(part[1]);
				int z = Integer.valueOf(part[2]);
				IRegion region = world.getChunkProvider().getRegion(x, z);
				copy.add(region);
			}
			has_regions = true;
			
			displayList = GL11.glGenLists(1);
			GL11.glNewList(displayList, GL11.GL_COMPILE);
			drawRegionGeometryOld(copy);
			GL11.glEndList();
//			drawRegionGeometryNew(copy);
		}
		
//		chunk_atlas.bind();
		GL11.glCallList(displayList);
//		chunk_atlas.unbind();
	}
	
//	void drawRegionGeometryNew(List<IRegion> copy) {
//		float yo = -0.1f;
//		
//		Map<IRegion, Integer> map = new HashMap<>();
//		
//		for(IRegion region : copy) {
//			if(!region.isLoaded()) continue;
//			BufferedImage bi = genImage(region);
//			
//			int id = chunk_atlas.addTexture(region.getX() + "." + region.getZ(), bi);
//			map.put(region, id);
//		}
//		
//		GL11.glNewList(displayList, GL11.GL_COMPILE);
//		GL11.glEnable(GL11.GL_TEXTURE);
//		GL11.glBegin(GL11.GL_TRIANGLES);
//		GL11.glColor3f(1, 1, 1);
//		for(Map.Entry<IRegion, Integer> entry : map.entrySet()) {
//			IRegion region = entry.getKey();
//			int id = entry.getValue();
//			
//			int rx = region.getX() * 512;
//			int rz = region.getZ() * 512;
//			
//			AtlasUv uv = chunk_atlas.getUv(id);
//			GL11.glTexCoord2f(uv.x1, uv.y1); GL11.glVertex3f(rx      , yo, rz      );
//			GL11.glTexCoord2f(uv.x1, uv.y2); GL11.glVertex3f(rx      , yo, rz + 512);
//			GL11.glTexCoord2f(uv.x2, uv.y2); GL11.glVertex3f(rx + 512, yo, rz + 512);
//			GL11.glTexCoord2f(uv.x1, uv.y1); GL11.glVertex3f(rx      , yo, rz      );
//			GL11.glTexCoord2f(uv.x2, uv.y2); GL11.glVertex3f(rx + 512, yo, rz + 512);
//			GL11.glTexCoord2f(uv.x2, uv.y1); GL11.glVertex3f(rx + 512, yo, rz      );
//		}
//		GL11.glEndList();
//		
//		chunk_atlas.compile();
//	}
	
	void drawRegionGeometryOld(List<IRegion> copy) {
		float yo = -0.1f;
		GL11.glBegin(GL11.GL_TRIANGLES);
		GL11.glColor3f(0.7f, 0.7f, 0.7f);
		for(IRegion region : copy) {
			if(!region.isLoaded()) continue;
			
			int rx = region.getX() * 512;
			int rz = region.getZ() * 512;
			
			final int len = 32 * 32;
			for(int i = 0; i < len; i++) {
				int x = i & 31;
				int z = i / 32;
				if(((x ^ z) & 1) != 0) {
					GL11.glColor3f(0.7f, 0.7f, 0.7f);
					if(region.hasChunk(x, z)) {
						int gx = rx + x * 16;
						int gz = rz + z * 16;
						
						GL11.glVertex3f(gx     , yo, gz     );
						GL11.glVertex3f(gx     , yo, gz + 16);
						GL11.glVertex3f(gx + 16, yo, gz + 16);
						
						GL11.glVertex3f(gx     , yo, gz     );
						GL11.glVertex3f(gx + 16, yo, gz + 16);
						GL11.glVertex3f(gx + 16, yo, gz     );
					}
				}
			}
		}
		
		GL11.glColor3f(0.5f, 0.5f, 0.5f);
		for(IRegion region : copy) {
			if(!region.isLoaded()) continue;
			
			int rx = region.getX() * 512;
			int rz = region.getZ() * 512;
			
			final int len = 32 * 32;
			for(int i = 0; i < len; i++) {
				int x = i & 31;
				int z = i / 32;
				
				if(((x ^ z) & 1) == 0) {
					if(region.hasChunk(x, z)) {
						int gx = rx + x * 16;
						int gz = rz + z * 16;
						
						GL11.glVertex3f(gx     , yo, gz     );
						GL11.glVertex3f(gx     , yo, gz + 16);
						GL11.glVertex3f(gx + 16, yo, gz + 16);
						
						GL11.glVertex3f(gx     , yo, gz     );
						GL11.glVertex3f(gx + 16, yo, gz + 16);
						GL11.glVertex3f(gx + 16, yo, gz     );
					}
				}
			}
		}
		
		GL11.glEnd();
	}
	
	BufferedImage genImage(IRegion region) {
		BufferedImage bi = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		final int len = 32 * 32;
		for(int i = 0; i < len; i++) {
			int x = i & 31;
			int z = i / 32;
			
			if(region.hasChunk(x, z)) {
				int col = ((x ^ z) & 1) != 0 ? 0xFFB2B2B2:0xFF7F7F7F;
				bi.setRGB(x, z, col);
			} else {
				bi.setRGB(x, z, 0);
			}
		}
		
		return bi;
	}
	
	void drawFrustumChunks(World world, Matrix4f projectionView, Vector3f camera, int radius) {
		int x = Math.floorDiv((int)camera.x, 16);
		int z = Math.floorDiv((int)camera.z, 16);
		final int xs = x - radius;
		final int xe = x + radius;
		final int zs = z - radius;
		final int ze = z + radius;
		
		FrustumIntersection intersect = new FrustumIntersection(projectionView);
		
		RenderUtil.drawFrustum(projectionView, camera, LwjglWindow.getWidth(), LwjglWindow.getHeight(), 20, 100);
		
		GL11.glLineWidth(1.0f);
		for(int i = xs; i <= xe; i++) {
			for(int j = zs; j <= ze; j++) {
				IChunk chunk = world.getChunk(i, j);
				
				if(chunk.isLoaded()) {
					GL11.glColor4f(1, 1, 1, 1);
				} else {
					GL11.glColor4f(1, 0, 0, 1);
				}
				
				for(int k = 0; k < 16; k++) {
					if(intersect.testAab(new Vector3f(i * 16, k * 16, j * 16), new Vector3f((i + 1) * 16, (k + 1) * 16, (j + 1) * 16))) {
						if(!chunk.isLoaded()) {
							RenderUtil.drawWireBlock(i * 16, 0, j * 16, 16, 16, 16);
							break;
						}
						
						if(!chunk.getSection(k).isLoaded()) {
							continue;
						}
						
						RenderUtil.drawWireBlock(i * 16, k * 16, j * 16, 16, 16, 16);
					}
				}
			}
		}
	}
}
