package org.spilth.minimalism;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

public class Minimalism implements ApplicationListener {
	private String[] levels = {"level02.tmx", "level03.tmx", "level04.tmx", "level01.tmx", "ending.tmx"};
	private int levelNumber = 0;
	private String message = null;
	
	private OrthographicCamera camera;
	private OrthogonalTiledMapRenderer renderer;
	private SpriteBatch batch;
	public TiledMap map;
	public Texture playerImage;
	private float deltaTime;
	private Player player;
	private int tileSize = 32;
	private int maxScore = 1000000;
	private int score;
	private int itemCount = 0;
	private int pickupPoints = maxScore / 10;
	
	private boolean complete = true;
	
	private FPSLogger fpsLogger = new FPSLogger();
	
	private BitmapFont font;

	private Pool<Rectangle> rectPool = new Pool<Rectangle>() {
		@Override
		protected Rectangle newObject() {
			return new Rectangle();
		}
	};

	private Array<Rectangle> tiles = new Array<Rectangle>();
	private Sound itemSound;
	private float coinPitch = 0.0f;

	private int viewportWidth = 1024;
	private int viewportHeight = 728;
	private int minCameraX = 0;
	private int minCameraY = 0;
	private int maxCameraX;
	private int maxCameraY;
	private int levelWidth;
	private int levelHeight;
	private Sound breakSound;
	private Sound jumpSound;
	private Music music;
	
	private int[] backgroundLayers = { 0,1,2 };
	private int[] foregroundLayers = { 3,4 };
	
	private int pickupIndex = 3;
	private int collisionIndex = 2;
	private int triggerIndex = 1;
	private Sound completeSound;
	private String levelName = "level02.tmx";
	
	@Override
	public void create() {
		float w = Gdx.graphics.getWidth();
		float h = Gdx.graphics.getHeight();
		
		camera = new OrthographicCamera(1, h/w);
		batch = new SpriteBatch();

		font = new BitmapFont(Gdx.files.internal("font.fnt"),Gdx.files.internal("font.png"),false);
		font.setColor(0, 0, 0, 1);

		itemSound = Gdx.audio.newSound(Gdx.files.internal("item.wav"));
		breakSound = Gdx.audio.newSound(Gdx.files.internal("break.wav"));
		jumpSound = Gdx.audio.newSound(Gdx.files.internal("jump.wav"));
		completeSound = Gdx.audio.newSound(Gdx.files.internal("complete.wav"));
		
		playerImage = new Texture(Gdx.files.internal("player.png"));

		music = Gdx.audio.newMusic(Gdx.files.internal("minimalism.ogg"));
		music.setLooping(true);
		music.play();
		
		resetLevel();
	}

	@Override
	public void dispose() {
		batch.dispose();
	}

	private void getTiles(int layerIndex, int startX, int startY, int endX, int endY, Array<Rectangle> tiles) {
		startX /= tileSize;
		startY /= tileSize;
		endX /= tileSize;
		endY /= tileSize;
		TiledMapTileLayer layer = (TiledMapTileLayer) map.getLayers().get(layerIndex);
		rectPool.freeAll(tiles);
		tiles.clear();
		for (int y = startY; y <= endY; y++) {
			for (int x = startX; x <= endX; x++) {
				Cell cell = layer.getCell(x, y);
				if (cell != null) {
					Rectangle rect = rectPool.obtain();
					rect.set(x * tileSize, y * tileSize, tileSize, tileSize);
					tiles.add(rect);
				}
			}
		}
	}

	@Override
	public void pause() {
	}

	private void pickupItem(Rectangle tile) {
		itemSound.play(1.0f, 1.0f + coinPitch, 0);
		TiledMapTileLayer layer = (TiledMapTileLayer) map.getLayers().get(pickupIndex);
		layer.setCell((int) tile.x / tileSize, (int) tile.y / tileSize, null);
		score -= pickupPoints;
		coinPitch -= 0.01f;
		itemCount += 1;
	}

	@Override
	public void render() {		
		Gdx.gl.glClearColor(1, 1, 1, 1);
		Gdx.gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
		
		deltaTime = Gdx.graphics.getDeltaTime();
		
		// PROCESS INPUT
		if ((Gdx.input.isKeyPressed(Keys.R))) {
			resetLevel();
		}
		
		
		if (!complete) {
			if ((Gdx.input.isKeyPressed(Keys.SPACE)) && player.grounded) {
				player.velocity.y += player.JUMP_VELOCITY;
				player.grounded = false;
				jumpSound.play();
			}
			
			if (player.grounded) {
				// If they're on the ground...
				if (Gdx.input.isKeyPressed(Keys.LEFT)) {
					player.velocity.x = -player.MAX_VELOCITY;
				}
	
				if (Gdx.input.isKeyPressed(Keys.RIGHT)) {
					player.velocity.x = player.MAX_VELOCITY;
				}
			} else {
				// If they're in the air control is more limited
				if (Gdx.input.isKeyPressed(Keys.LEFT)) {
					if (player.velocity.x >= 0)
						player.velocity.x = -player.MAX_VELOCITY / 4;
				}
	
				if (Gdx.input.isKeyPressed(Keys.RIGHT)) {
					if (player.velocity.x <= 0)
						player.velocity.x = player.MAX_VELOCITY / 4;
				}
			}
			
			player.velocity.add(0, -9.0f);
			
			// clamp the velocity to the maximum, x-axis only
			if (Math.abs(player.velocity.x) > player.MAX_VELOCITY) {
				player.velocity.x = Math.signum(player.velocity.x) * player.MAX_VELOCITY;
			}
	
			// clamp the velocity to 0 if it's < 1
			if (Math.abs(player.velocity.x) < 1) {
				player.velocity.x = 0;
			}
			
			player.velocity.scl(deltaTime);
		} else {
			if (Gdx.input.isKeyPressed(Keys.N)) {
				loadNextLevel();
			}
		}
		
		Rectangle playerRect = rectPool.obtain();
		playerRect.set(player.position.x, player.position.y, player.width, player.height);
		
		int startX, startY, endX, endY;

		// Left/Right Movement
		if (player.velocity.x > 0) {
			// Right
			startX = endX = (int) (player.position.x + player.width + player.velocity.x);
		} else {
			// Left
			startX = endX = (int) (player.position.x + player.velocity.x);
		}
		startY = (int) (player.position.y);
		endY = (int) (player.position.y + player.height);
		getTiles(collisionIndex, startX, startY, endX, endY, tiles);

		// Figure out where they want to be...
		playerRect.x += player.velocity.x;
		
		// See if anything is blocking where they want to go
		for (Rectangle tile : tiles) {
			if (playerRect.overlaps(tile)) {
				player.velocity.x = 0;
				break;
			}
		}
		
		// Pickup Collision
		startX = (int) (player.position.x - player.width);
		endX = (int) (player.position.x + (player.width * 2.0f));
		startY = (int) (player.position.y - player.height);
		endY = (int) (player.position.y + (player.width * 2.0f));
			
		getTiles(pickupIndex, startX, startY, endX, endY, tiles);
		for (Rectangle tile : tiles) {
			if (playerRect.overlaps(tile)) {
				pickupItem(tile);
			}
		}
		
		getTiles(triggerIndex, startX, startY, endX, endY, tiles);
		for (Rectangle tile : tiles) {
			if (playerRect.overlaps(tile)) {
				complete = true;
				//music.stop();
				completeSound.play();
			}
		}
		
		playerRect.x = player.position.x;

		// Up/Down
		if (player.velocity.y > 0) {
			// Up
			startY = endY = (int) (player.position.y + player.height + player.velocity.y);
		} else {
			// Down
			startY = endY = (int) (player.position.y + player.velocity.y);
		}

		// Disable jumping when falling
		if (player.velocity.y < -1) {
			player.grounded = false;
		}

		startX = (int) (player.position.x);
		endX = (int) (player.position.x + player.width);

		getTiles(collisionIndex, startX, startY, endX, endY, tiles);

		// Figure out where they want to be
		playerRect.y += player.velocity.y;

		// See if anything is blocking where they want to go
		for (Rectangle tile : tiles) {
			if (playerRect.overlaps(tile)) {
				// we actually reset the player y-position here
				// so it is just below/above the tile we collided with
				// this removes bouncing :)
				if (player.velocity.y > 0) {
					player.position.y = tile.y - player.height;
					// we hit a block jumping upwards, let's destroy it!
					TiledMapTileLayer layer = (TiledMapTileLayer) map.getLayers().get(collisionIndex);
					Cell block = layer.getCell((int) tile.x / tileSize, (int) tile.y / tileSize);
					if (block.getTile().getProperties().get("breakable", "false", String.class).equals("true")) {
						layer.setCell((int) tile.x / tileSize, (int) tile.y / tileSize, null);
						breakSound.play();
					}
				} else {
					player.position.y = tile.y + tile.height;

					// if we hit the ground, mark us as grounded so we can jump
					player.grounded = true;
				}
				player.velocity.y = 0;
				break;
			}
		}
		rectPool.free(playerRect);

		if (player.grounded) player.velocity.x *= player.DAMPING;

		player.position.add(player.velocity);
		player.velocity.scl(1 / deltaTime);

		// Center camera on player but don't let camera move beyond the boundaries of the visible map
		camera.position.x = MathUtils.clamp(player.position.x, minCameraX, maxCameraX);
		camera.position.y = MathUtils.clamp(player.position.y, minCameraY, maxCameraY);
		camera.update();

		renderer.setView(camera);
		renderer.render(backgroundLayers);

		batch.setProjectionMatrix(camera.combined);
		batch.begin();
		batch.draw(playerImage, player.position.x, player.position.y, player.width, player.height);
		batch.end();

		renderer.render(foregroundLayers);

		batch.setProjectionMatrix(camera.projection);
		batch.begin();
		font.draw(batch, "Score: " + Integer.toString(score), -384 , -336);
		font.draw(batch, "Items: " + Integer.toString(itemCount), 256 , -336);
		if (complete) {
			font.draw(batch, "LEVEL COMPLETED!", -128 , 0);
			font.draw(batch, "PRESS 'N' FOR NEXT LEVEL", -128 , -32);
		} else {
			if (message != null) {
				font.draw(batch, message, -384 , 336);
			}
		}
		batch.end();
		
		
		fpsLogger.log();
	}
	
	private void loadNextLevel() {
		levelNumber++;
		resetLevel();
	}

	private void resetLevel() {
		complete = false;
		score = maxScore;
		player = new Player();
		player.position.x = 32;
		player.position.y = 32;
		itemCount = 0;

		levelName = levels[levelNumber];
		map = new TmxMapLoader().load(levelName);
		
		TiledMapTileLayer mainLayer = (TiledMapTileLayer) map.getLayers().get(0);
		tileSize = (int) mainLayer.getTileWidth();
		levelWidth = mainLayer.getWidth() * tileSize;	
		levelHeight = mainLayer.getHeight() * tileSize;
		
		message = map.getProperties().get("message", null, String.class);
		
		// Set up min/max position of Camera
		minCameraX = viewportWidth / 2;
		minCameraY = viewportHeight / 2;
		maxCameraX = levelWidth - minCameraX;
		maxCameraY = levelHeight - minCameraY;
		
		renderer = new OrthogonalTiledMapRenderer(map);
		camera.setToOrtho(false, viewportWidth, viewportHeight);

	    //music.play();
	}

	@Override
	public void resize(int width, int height) {
	}

	@Override
	public void resume() {
	}
}
