package org.spilth.minimalism;

import com.badlogic.gdx.math.Vector2;

class Player {
	float width;
	float height;

	float MAX_VELOCITY = 360f;
	float JUMP_VELOCITY = 400;
	float DAMPING = 0.60f;

	Vector2 position = new Vector2();
	Vector2 velocity = new Vector2();
	
	boolean grounded = false;
	
	float stateTime = 0;
	
	public Player() {
		width = 32;
		height = 32;
	}
}
