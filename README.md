![Image]([https://github.com/user-attachments/assets/13030ecc-d58a-4d3f-8c20-6f41053552e3](https://www.youtube.com/watch?v=0GEIQTQPY00))

# Whiteboard App (IFP)

Offline whiteboard for Interactive Flat Panels (IFP) with local JSON persistence.

## Features
- Freehand drawing with smooth strokes (touch/stylus)
- Eraser tool (stroke erase + text deletion; includes partial text erase approximation)
- Stroke width control
- 6-color palette
- Insert and edit:
  - Shapes: rectangle, circle, line, polygon (polygon with 5+ sides)
  - Text: insert via dialog, tap to edit, drag to move
- Save/load whiteboard state locally:
  - Saves `.json` files with timestamp names
  - Load picker lists saved JSON files
- Undo/Redo with gesture batching (drag counts as one step)
- Landscape-only activity

## Architecture (Clean Architecture style)
- `presentation/` : UI (custom view + activity) and `WhiteboardViewModel`
- `domain/` : pure Kotlin models (`StrokeEntity`, `ShapeEntity`, `TextEntity`, `WhiteboardState`)
- `data/` : local file storage + repository (`LocalWhiteboardStorage`, `WhiteboardRepositoryImpl`)

## JSON file format
Whiteboard state is stored as a structured JSON object:

```json
{
  "strokes": [
    { "points": [[10,10],[15,20]], "color": "#FF0000", "width": 3 }
  ],
  "shapes": [
    { "type": "rectangle", "topLeft": [50,50], "bottomRight": [150,100], "color": "#0000FF" }
  ],
  "texts": [
    { "text": "Hello IFP!", "position": [300,400], "color": "#000000", "size": 24 }
  ]
}
```

## Setup / Run
1. Open the project in Android Studio / IntelliJ (or build with Gradle).
2. Ensure `local.properties` points to your Android SDK (Gradle uses `sdk.dir`).
3. Build and run on a device/emulator.

## IFP deployment notes (65" - 85")
- App is locked to landscape mode.
- Toolbar and touch targets scale through resource qualifiers:
  - `res/values/dimens.xml`
  - `res/values-sw600dp/dimens.xml`
- For classroom panels, use full-screen mode and disable system gesture overlays if device policy allows.
- Recommended runtime setup:
  - stylus preferred for writing
  - finger for shape move/resize and toolbar actions

## Architecture overview
- `presentation/`
  - `MainActivity`: toolbar interactions, save/load/export triggers
  - `WhiteboardCanvasView`: rendering and touch input handling
  - `WhiteboardViewModel`: state orchestration, undo/redo, tool config
- `domain/model/`
  - canvas entities and hit-testing (`StrokeEntity`, `ShapeEntity`, `TextEntity`)
- `data/`
  - `WhiteboardRepositoryImpl` and local JSON file persistence
- `data/local/`
  - `LocalWhiteboardStorage` for save/list/load operations

## Sample files
Two sample whiteboards are included under:
`app/src/main/assets/sample_whiteboards/`

These are provided for reference (you can copy them into the app’s local storage folder to load them).

