<div align="center">
  <img src="art/ic_launcher-web.png" alt="" width="96px" height="96px">
</div>

<p align="center"><img src="https://github.com/SimonMarquis/AR-Toolbox/actions/workflows/android.yml/badge.svg" alt="Android CI"></p>
<h3 align="center">AR Toolbox</h3>
<p align="center">
  ARCore & Sceneform Playground<br>
  <a href='https://play.google.com/store/apps/details?id=fr.smarquis.ar_toolbox'><img alt='Get it on Google Play' src='art/google-play-badge.png'/></a>
</p>

<br>

| Scene | Models | Options | Settings |
|---|---|---|---|
| ![](art/screenshot_scene.png) | ![](art/screenshot_scene_models.png) | ![](art/screenshot_scene_options.png) | ![](art/screenshot_scene_settings.png) |

| Model chooser | Augmented Face | Native Viewer | Web Viewer |
|---|---|---|---|
| ![](art/screenshot_models_gltf.png) | ![](art/screenshot_faces.png) | ![](art/screenshot_viewer_native.png) | ![](art/screenshot_viewer_web.png) |

### Features

- **Scene debug**: Camera position, Camera rotation, Anchors, Planes, Points
- **[Scene configuration](https://developers.google.com/sceneform/develop/build-scene)**: Sunlight, Shadows, Plane renderer, Selection visualizer
- **[3D models](https://developers.google.com/sceneform/develop/create-renderables)**: Sphere, Cylinder, Cube, Layout, sfa, Freehand drawing, glTF, Video
- **[Material properties](https://developers.google.com/sceneform/develop/standard_properties.pdf)**: Color, Metallic, Roughness, Reflectance
- **[Gestures](https://developers.google.com/sceneform/develop/build-scene)**: Drag, Rotate, Scale
- **[Measurements](https://developers.google.com/sceneform/develop/create-renderables)**: Measure polylines
- **[Augmented images](https://developers.google.com/ar/develop/java/augmented-images/)**: Tracks the application logo
- **[Augmented faces](https://developers.google.com/ar/develop/java/augmented-faces/)**: Identify regions of a detected face
- **[Depth points](https://developers.google.com/ar/develop/java/depth/developer-guide#depth_hit_test)**: More accurate hit tests
- **[Native & web scene viewers](https://developers.google.com/ar/develop/java/scene-viewer)**: Interactive models in native or on the web
- **[Cloud anchors](https://developers.google.com/ar/develop/java/cloud-anchors/overview-android)**: Host, share and resolve anchors
- **[PointCloud](https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/PointCloud)**: Feature points
- **[Screen capture & recording](https://developers.google.com/sceneform/develop/video-recording)**: 480p, 720p, 1080p, 2160p
- **[Performance overlay](https://developers.google.com/ar/develop/java/debugging-tools/performance-overlay)**: CPU usage, memory, temperature

## Credits

- [ARCore](https://github.com/google-ar/arcore-android-sdk)
- [Sceneform](https://github.com/google-ar/sceneform-android-sdk)
- [ColorSeekBar](https://github.com/divyanshub024/ColorSeekBar)
- [sceneform-samples](https://github.com/googlesamples/sceneform-samples)
- [glTF-Sample-Models](https://github.com/KhronosGroup/glTF-Sample-Models)
- [Poly](https://poly.google.com/) [🪦](https://support.google.com/poly/answer/10192635)
- [Poly Pizza](https://poly.pizza/)
- [\<model-viewer\>](https://github.com/GoogleWebComponents/model-viewer)

## License

```
Copyright 2019 Simon Marquis

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
