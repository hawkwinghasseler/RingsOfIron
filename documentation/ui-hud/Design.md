# UI & Viewports Module Design

## Overview
The `ui-hud` module handles all visual rendering that isn't directly part of the physical game world. This includes menus, piloting HUDs, interior interaction prompts, and viewport scaling management.

## Implementation Dependencies
- **`core-engine`**: Ashley ECS to track entities needing UI markers.
- **`physics-exterior`**: Needs the `WorldCamera` to project Box2D world coordinates into screen coordinates.
- **`interior-grid`**: Needs access to the ship's 2D tile layout to generate the wireframe mini-map.
- **External Libraries:** LibGDX `Scene2D.UI`.

## Implementation Guide
To implement this module independently (mocking data providers):

1. **Viewport & Camera Management:**
   - Create a `ViewportManager` class.
   - Initialize a `WorldCamera` coupled with an `ExtendViewport` (for rendering space/grid).
   - Initialize a `UICamera` coupled with a `ScreenViewport` (for crisp 1:1 pixel UI rendering).
   - Ensure the `resize(width, height)` method updates both viewports properly to handle window scaling.

2. **Scene2D Setup:**
   - Initialize a LibGDX `Stage` using the `ScreenViewport`.
   - Load a standard `Skin` generated via a TextureAtlas defining 9-patch borders, fonts, and button styles.

3. **Exterior Piloting HUD (Projections):**
   - Create a `HudRenderSystem`.
   - For spatial UI elements (e.g., target lead reticles, station nameplates), grab the target entity's world position.
   - Use `worldCamera.project(worldCoords)` to translate the Box2D coordinate to a screen pixel coordinate.
   - Position a Scene2D `Image` or `Label` widget at that calculated pixel coordinate. Update this every frame.

4. **Interior Wireframe Overlay:**
   - Create a custom Scene2D `Widget` called `WireframeMinimap`.
   - Pass the player's `ShipGridComponent` data to this widget.
   - Override the `draw(Batch batch, float parentAlpha)` method. Iterate through the grid bounds and draw colored rectangles or lines representing rooms.
   - Color code tiles based on their state (e.g., red for `hasFire`, blue for `hasVacuum`). Render this widget in the bottom-left corner of the `Stage`.

5. **Context Menus:**
   - Implement a system that checks the distance between the player's avatar and interactable consoles on the grid.
   - If distance < `INTERACT_RADIUS`, spawn a contextual "Press [E] to Access" tooltip via Scene2D.

## Proposed Components
- `HudMarkerComponent`: Attached to entities that need a UI marker (e.g., enemies, mission waypoints).
- `InteractableConsoleComponent`: Defines the UI menu layout to open when a player triggers this interior tile.

## Proposed Systems
- `HudRenderSystem`: Projects 3D/2D world coordinates to UI space and updates widget positions.
- `WireframeSyncSystem`: Reads ship state and feeds it to the Scene2D wireframe minimap.
