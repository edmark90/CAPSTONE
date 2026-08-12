# Plant Identification Integration Documentation

## Overview
This document describes the integration of a Plant Identification stage before the existing Disease Classification pipeline. The new architecture ensures that disease predictions are filtered based on the detected plant type (Tomato, Eggplant, or Chili).

## Architecture

### Pipeline Flow
```
Camera Capture
    ↓
Plant Identification (plantidentification.tflite)
    ↓
Plant Confidence Check (≥ 80%)
    ↓ (if passes)
Disease Classification (model_int8.tflite)
    ↓
Filter Disease Classes by Plant Type
    ↓
Final Result (Plant + Disease)
```

### File Structure

#### New ML Files (`app/src/main/java/com/example/smartplantcare/ML/`)
- **`PlantType.kt`** - Enum defining plant types (TOMATO, EGGPLANT, CHILI, UNKNOWN)
- **`PlantPrediction.kt`** - Data class for plant identification results
- **`DetectionResult.kt`** - Sealed class for pipeline results (PlantRejected, DiseaseRejected, DiseaseAccepted)
- **`AIConfig.kt`** - Centralized configuration for all thresholds and model parameters
- **`PlantIdentifier.kt`** - Plant identification model inference engine
- **`DiseaseFilter.kt`** - Filters disease classes based on detected plant type

#### Updated ML Files
- **`LeafClassifier.kt`** - Updated to accept optional `PlantType` parameter for filtering disease predictions

#### New UI Files (`app/src/main/java/com/example/smartplantcare/ui/theme/screens/homescreen/plantidentify/`)
- **`DiseaseDetectionViewModel.kt`** - ViewModel managing the complete detection pipeline
- **`DiseaseDetectionScreen.kt`** - Camera capture screen for disease detection
- **`DiseaseResultScreen.kt`** - Result screen showing plant and disease information

#### Assets
- **`app/src/main/assets/plantidentification.tflite`** - Plant identification model (already present)

## Key Components

### 1. PlantType Enum
```kotlin
enum class PlantType {
    TOMATO,      // Index 0 in plantidentification.tflite
    EGGPLANT,    // Index 1 in plantidentification.tflite
    CHILI,       // Index 2 in plantidentification.tflite
    UNKNOWN
}
```

### 2. AIConfig Configuration
```kotlin
object AIConfig {
    const val PLANT_THRESHOLD = 0.80f              // 80% confidence required
    const val DISEASE_CONFIDENCE_THRESHOLD = 0.52f  // 52% confidence required
    const val DISEASE_MARGIN_THRESHOLD = 0.12f     // 12% margin required
}
```

### 3. DiseaseFilter Class Indices
Based on `class_mapping.json`:
- **Chili**: indices 0-5 (6 classes)
- **Eggplant**: indices 6-11 (6 classes)  
- **Tomato**: indices 12-21 (10 classes)

### 4. DetectionResult States
```kotlin
sealed class DetectionResult {
    data class PlantRejected(...)      // Plant confidence < 80%
    data class DiseaseRejected(...)    // Disease confidence < 52%
    data class DiseaseAccepted(...)    // Both plant and disease identified
}
```

## Integration Steps

### Step 1: Add Navigation Entry
In your navigation graph, add the new disease detection screen:

```kotlin
composable("disease_detection") {
    DiseaseDetectionScreen(onBack = { navController.popBackStack() })
}
```

### Step 2: Update ModelConfig (Optional)
The existing `ModelConfig.kt` can be replaced with `AIConfig.kt` for centralized configuration, but this is optional since `LeafClassifier` still references its own constants for backward compatibility.

### Step 3: Use the New Pipeline
To use the new disease detection pipeline:

```kotlin
@Composable
fun DiseaseDetectionEntry() {
    DiseaseDetectionScreen(onBack = { /* handle back */ })
}
```

## Backward Compatibility

### Existing LeafClassifier API
The existing `LeafClassifier` API remains fully backward compatible:

```kotlin
// Old API still works (no filtering)
val result = leafClassifier.classify(bitmap, rotationDegrees)
val result = leafClassifier.classifyFromUri(uri)

// New API with plant filtering
val result = leafClassifier.classify(bitmap, rotationDegrees, plantType)
val result = leafClassifier.classifyFromUri(uri, plantType)
```

### Existing Plant Identification
The existing `PlantIdentifyViewModel` and `PlantIdentifyScreen` (using PlantNet API) remain unchanged and can coexist with the new on-device disease detection.

## Performance Considerations

### Bitmap Reuse
The pipeline reuses the same bitmap for both plant identification and disease classification to avoid unnecessary copies:

```kotlin
val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
val plantPrediction = plantIdentifier.identify(bitmap)  // Reuses bitmap
val diseaseResult = leafClassifier.classify(bitmap, plantType)  // Reuses bitmap
```

### Background Thread Execution
All ML inference runs on `Dispatchers.IO` to avoid blocking the UI:

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    // Plant identification and disease classification run here
}
```

### Model Initialization
Models are initialized once in the ViewModel's `init` block and reused for all detections.

## Error Handling

### Plant Identification Failure
If plant confidence is below 80%, the pipeline stops and shows:
```
"Unable to confidently identify the plant. Please capture a clearer image."
```

### Disease Classification Failure
If disease confidence is below 52% or margin is below 12%, the pipeline shows:
```
"Unable to confidently identify the disease. Please try again with a clearer image."
```

### Model Initialization Failure
If models fail to initialize, an error state is shown with the exception message.

## Testing Checklist

- [ ] Verify `plantidentification.tflite` is in `app/src/main/assets/`
- [ ] Test plant identification with Tomato, Eggplant, and Chili images
- [ ] Verify plant confidence threshold (80%) works correctly
- [ ] Test disease classification with filtered results
- [ ] Verify disease confidence threshold (52%) works correctly
- [ ] Test UI states: Idle → Scanning → Result (all three result types)
- [ ] Verify camera capture and gallery upload both work
- [ ] Test with images that should be rejected (low confidence)
- [ ] Verify host plant in result matches detected plant type
- [ ] Test performance on background threads

## Troubleshooting

### Issue: "Plant identification model not found"
**Solution**: Ensure `plantidentification.tflite` is in `app/src/main/assets/`

### Issue: "No valid disease class found for [Plant]"
**Solution**: This occurs when the disease model's top prediction doesn't belong to the detected plant. Check that `class_mapping.json` indices match `DiseaseFilter.kt` constants.

### Issue: Low confidence rejections
**Solution**: Adjust thresholds in `AIConfig.kt`:
- `PLANT_THRESHOLD` for plant identification
- `DISEASE_CONFIDENCE_THRESHOLD` for disease classification
- `DISEASE_MARGIN_THRESHOLD` for disease margin

### Issue: Performance is slow
**Solution**: 
- Ensure models are running on `Dispatchers.IO`
- Check `TFLITE_NUM_THREADS` in `AIConfig.kt` (default: 4)
- Verify bitmap is not being copied unnecessarily

## Future Enhancements

1. **Model Quantization**: Consider quantizing `plantidentification.tflite` to INT8 for faster inference
2. **Batch Processing**: Add support for processing multiple images in sequence
3. **Confidence Calibration**: Collect user feedback to calibrate thresholds
4. **Additional Plant Types**: Extend to support more plant types as needed
5. **Result Caching**: Cache recent results to avoid re-processing

## Summary

The new plant identification integration provides:
- ✅ Clean separation of concerns (plant identification vs disease classification)
- ✅ Configurable thresholds via `AIConfig`
- ✅ Backward compatible with existing `LeafClassifier` API
- ✅ Proper error handling and user feedback
- ✅ Efficient bitmap reuse and background execution
- ✅ Type-safe detection results via sealed classes
- ✅ Plant-specific disease filtering via `DiseaseFilter`

The existing disease model (`model_int8.tflite`) and preprocessing remain completely unchanged, ensuring no regression in existing functionality.
