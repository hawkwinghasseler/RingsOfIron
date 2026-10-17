package com.hawkwing.RingsOfIron;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.DistanceJoint;
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef;
import com.badlogic.gdx.physics.box2d.joints.MouseJoint;
import com.badlogic.gdx.physics.box2d.joints.MouseJointDef;

public class Box2dPrecisionTest extends ApplicationAdapter {

    private World world;
    private Box2DDebugRenderer debugRenderer;
    private OrthographicCamera camera;

    // Body pairs & Platforms
    private Body originBodyA, originBodyB;
    private Body farBodyA, farBodyB;
    private Body originPlatform, farPlatform;
    private DistanceJoint originJoint, farJoint;
    private Body groundBody; // Anchor for mouse joint

    // Mouse interaction variables
    private MouseJoint mouseJoint = null;
    private Fixture hitFixture = null;
    private Vector3 tmpVector = new Vector3();

    // Simulation configuration
    private float farCoordinateX = 200000f; // 200 km out where float precision severely degrades
    private int activeViewTarget = 1;       // 1 = Origin, 2 = Far-away
    private float logTimer = 0f;

    @Override
    public void create() {
        Box2D.init();
        world = new World(new Vector2(0, -9.8f), true);
        debugRenderer = new Box2DDebugRenderer();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 10f, 10f * ((float) Gdx.graphics.getHeight() / Gdx.graphics.getWidth()));

        // Create a dummy static body for mouse joint anchoring
        BodyDef groundDef = new BodyDef();
        groundBody = world.createBody(groundDef);

        // 1. Setup Near-Origin Test (x = 0m)
        originPlatform = createStaticFloor(0f, 0f, 10f);
        originBodyA = createDynamicBody(0f, 2f);
        originBodyB = createDynamicBody(1.5f, 2f);
        originJoint = createDistanceJoint(originBodyA, originBodyB);

        // 2. Setup Far-Distance Test (x = farCoordinateX)
        rebuildFarSystem();

        Gdx.app.log("PrecisionScene", "--- CONTROLS ---");
        Gdx.app.log("PrecisionScene", "Press [1]: View Origin System (0, 0)");
        Gdx.app.log("PrecisionScene", "Press [2]: View Far-Away System (" + farCoordinateX + ", 0)");
        Gdx.app.log("PrecisionScene", "Press [UP/DOWN]: Change far coordinate distance (auto-resets positions)");
        Gdx.app.log("PrecisionScene", "Mouse Click & Drag: Pull objects with a spring joint to test physics stability");
    }

    private void rebuildFarSystem() {
        // Clean up old far bodies/joints/platforms if rebuilding
        if (farJoint != null) world.destroyJoint(farJoint);
        if (farBodyA != null) world.destroyBody(farBodyA);
        if (farBodyB != null) world.destroyBody(farBodyB);
        if (farPlatform != null) world.destroyBody(farPlatform);

        farPlatform = createStaticFloor(farCoordinateX, 0f, 10f);
        farBodyA = createDynamicBody(farCoordinateX, 2f);
        farBodyB = createDynamicBody(farCoordinateX + 1.5f, 2f);
        farJoint = createDistanceJoint(farBodyA, farBodyB);

        Gdx.app.log("PrecisionScene", "Far system reset & rebuilt at X = " + farCoordinateX);
    }

    private Body createDynamicBody(float x, float y) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);

        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(0.4f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 2.0f;
        fixtureDef.friction = 0.4f;
        fixtureDef.restitution = 0.3f;

        body.createFixture(fixtureDef);
        shape.dispose();
        return body;
    }

    private Body createStaticFloor(float centerX, float y, float width) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(centerX, y);

        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width / 2f, 0.2f);

        body.createFixture(shape, 0.0f);
        shape.dispose();
        return body;
    }

    private DistanceJoint createDistanceJoint(Body bodyA, Body bodyB) {
        DistanceJointDef jointDef = new DistanceJointDef();
        jointDef.initialize(bodyA, bodyB, bodyA.getPosition(), bodyB.getPosition());
        jointDef.collideConnected = true;
        jointDef.frequencyHz = 4.0f;
        jointDef.dampingRatio = 0.5f;
        return (DistanceJoint) world.createJoint(jointDef);
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1)) {
            activeViewTarget = 1;
            Gdx.app.log("PrecisionScene", "Switched view to: Origin (0,0)");
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_2)) {
            activeViewTarget = 2;
            Gdx.app.log("PrecisionScene", "Switched view to: Far-Away (" + farCoordinateX + ",0)");
        }

        // Adjust distance with Up/Down arrows and force reset
        boolean needRebuild = false;
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            farCoordinateX += 50000f;
            needRebuild = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) && farCoordinateX > 50000f) {
            farCoordinateX -= 50000f;
            needRebuild = true;
        }
        if (needRebuild) {
            rebuildFarSystem();
        }

        // Handle Mouse Input for Dragging Bodies
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            camera.unproject(tmpVector.set(Gdx.input.getX(), Gdx.input.getY(), 0));
            hitFixture = null;

            world.QueryAABB(new QueryCallback() {
                @Override
                public boolean reportFixture(Fixture fixture) {
                    if (fixture.testPoint(tmpVector.x, tmpVector.y)) {
                        hitFixture = fixture;
                        return false; // Stop querying
                    }
                    return true;
                }
            }, tmpVector.x - 0.001f, tmpVector.y - 0.001f, tmpVector.x + 0.001f, tmpVector.y + 0.001f);

            if (hitFixture != null && !hitFixture.getBody().getType().equals(BodyDef.BodyType.StaticBody)) {
                Body clickedBody = hitFixture.getBody();
                MouseJointDef md = new MouseJointDef();
                md.bodyA = groundBody;
                md.bodyB = clickedBody;
                md.collideConnected = true;
                md.maxForce = 1000.0f * clickedBody.getMass();
                md.target.set(tmpVector.x, tmpVector.y);
                
                mouseJoint = (MouseJoint) world.createJoint(md);
                clickedBody.setAwake(true);
            }
        }

        // Update mouse joint target on drag
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            if (mouseJoint != null) {
                camera.unproject(tmpVector.set(Gdx.input.getX(), Gdx.input.getY(), 0));
                mouseJoint.setTarget(new Vector2(tmpVector.x, tmpVector.y));
            }
        } else if (mouseJoint != null) {
            // Release mouse joint on button release
            world.destroyJoint(mouseJoint);
            mouseJoint = null;
        }
    }

    @Override
    public void render() {
        handleInput();

        world.step(1f / 60f, 6, 2);

        // Center camera on the active platform with a vertical offset (so platform sits near the bottom)
        Body activePlatform = (activeViewTarget == 1) ? originPlatform : farPlatform;
        Vector2 targetPos = activePlatform.getPosition();
        float verticalOffset = 2.5f; // shifts camera up so floor rests lower on the viewport
        
        camera.position.x += (targetPos.x - camera.position.x) * 0.5f;
        camera.position.y += ((targetPos.y + verticalOffset) - camera.position.y) * 0.5f;
        camera.update();

        Gdx.gl.glClearColor(0.15f, 0.15f, 0.18f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        debugRenderer.render(world, camera.combined);

        // Log precision error metrics
        logTimer += Gdx.graphics.getDeltaTime();
        if (logTimer >= 1.0f) {
            logTimer = 0f;
            float originDist = originBodyA.getPosition().dst(originBodyB.getPosition());
            float farDist = farBodyA.getPosition().dst(farBodyB.getPosition());
            
            Gdx.app.log("Metrics", String.format(
                "Origin Joint Len: %.4fm (Err: %.4f) | Far Joint Len: %.4fm (Err: %.4f) [X: %.0f]",
                originDist, Math.abs(originDist - 1.5f),
                farDist, Math.abs(farDist - 1.5f),
                farCoordinateX
            ));
        }
    }

    @Override
    public void resize(int width, int height) {
        float viewportHeight = 10f;
        float viewportWidth = viewportHeight * ((float) width / height);
        camera.setToOrtho(false, viewportWidth, viewportHeight);
    }

    @Override
    public void dispose() {
        if (world != null) world.dispose();
        if (debugRenderer != null) debugRenderer.dispose();
    }
}