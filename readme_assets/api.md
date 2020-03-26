# Classes/Enums
* [`RDT`](#RDT)
* [`ExposureResult`](#exposureResult)
* [`SizeResult`](#sizeResult)
* [`CaptureResult`](#rdtCaptureResult)
* [`InterpretationResult`](#rdtInterpretationResult)

# Methods
* [`configureCamera()`](#configureCamera)
* [`calculateBrightness()`](#calculateBrightness)
* [`checkBrightness()`](#checkBrightness)
* [`calculateSharpness()`](#calculateSharpness)
* [`checkSharpness()`](#checkSharpness)
* [`checkSizePositionOrientation()`](#checkSizePositionOrientation)
* [`checkFiducial()`](#checkFiducial)
* [`detectRDT()`](#detectRDT)
* [`captureRDT()`](#captureRDT)
* [`enhanceResultWindow()`](#enhanceResultWindow)
* [`interpretRDT()`](#interpretRDT)

## RDT
**Signature:** `RDT(Context context, String rdtName)`  
**Purpose:** Holds all of the parameters that are loaded from the configuration file for the RDT  
**Parameters:**
* `Context context`: the `Context` object for the app's `Activity` 
* `String rdtName`: the `String` used to reference the RDT design in `config.json`

## ExposureResult
**Signature:** `enum ExposureResult`  
**Purpose:** TODO  
**Possible Values:**
* `UNDER_EXPOSED`: 
* `NORMAL`:
* `OVER_EXPOSED`:

## SizeResult
**Signature:** `enum SizeResult`  
**Purpose:** TODO  
**Possible Values:**
* `RIGHT_SIZE`: 
* `LARGE`:
* `SMALL`:
* `INVALID`:

## CaptureResult
**Signature:** `CaptureResult(boolean allChecksPassed, Mat resultMat, boolean fiducial, ExposureResult exposureResult, SizeResult sizeResult, boolean isCentered, boolean isRightOrientation, double angle, boolean isSharp, boolean isShadow, MatOfPoint2f boundary, boolean flashEnabled)`  
**Purpose:** Holds all of the parameters that describe whether a candidate video framed passed all of the quality checks  
**Parameters:**
* `boolean allChecksPassed`: xxx
* `Mat resultMat`: xxx
* `boolean fiducial`: xxx
* `ExposureResult exposureResult`: xxx
* `SizeResult sizeResult`: xxx
* `boolean isCentered`: xxx
* `boolean isRightOrientation`: xxx
* `double angle`: xxx
* `boolean isSharp`: xxx
* `boolean isShadow`: xxx
* `MatOfPoint2f boundary`: xxx
* `boolean flashEnabled`: xxx

## InterpretationResult
**Signature:** `InterpretationResult(Mat resultMat, boolean topLine, boolean middleLine, boolean bottomLine)`  
**Purpose:** xxx  
**Parameters:**
* `Mat resultMat`: a cropped version of the image known to have a clear RDT in the video frame so that only the result window is showing
* `boolean topLine`: whether the top line was detected in the result window
* `boolean middleLine`: whether the middle line was detected in the result window
* `boolean bottomLine`: whether the bottom line was detected in the result window

## configureCamera()
**Signature:** `xxx`  
**Purpose:** xxx  
**Parameters:**
* `xxx`: xxx

**Returns:**
* `xxx`: xxx

## calculateBrightness()
**Signature:** `float[] mBuff = calculateBrightness(Mat input)`  
**Purpose:** Calculates the brightness histogram of the candidate video frame  
**Parameters:**
* `Mat input`: the candidate video frame

**Returns:**
* `float[] mBuff`: a 256-element histogram that quantifies the number of pixels at each brightness level for the greyscale version of `input`

## checkBrightness()
**Signature:** `ExposureResult exposureResult = checkBrightness(Mat input)`  
**Purpose:** Determines whether the candidate video frame has sufficient lighting without being too bright  
**Parameters:**
* `Mat input`: the candidate video frame

**Returns:**
* `ExposureResult exposureResult`: whether `input` satisfies the brightness thresholds in the configuration file.

### calculateSharpness()
**Signature:** `double sharpness = calculateSharpness(Mat input)`  
**Purpose:** Calculates the Laplacian variance of the candidate video frame  
**Parameters:**
* `Mat input`: the candidate video frame

**Returns:**
* `double sharpness`: the Laplacian variance of `input`

## checkSharpness()
**Signature:** `boolean isSharp = checkSharpness(Mat input)`  
**Purpose:** Determines whether the candidate video frame is focused  
**Parameters:**
* `Mat input`: the candidate video frame

**Returns:**
* `boolean isSharp`: whether `input` satisfies the sharpness threshold specified in the configuration file

## checkSizePositionOrientation()
**Signature:** `xxx`  
**Purpose:** xxx  
**Parameters:**
* `xxx`: xxx

**Returns:**
* `xxx`: xxx

## checkFiducial()
**Signature:** `xxx`  
**Purpose:** xxx  
**Parameters:**
* `xxx`: xxx

**Returns:**
* `xxx`: xxx

## detectRDT()
**Signature:** `xxx`  
**Purpose:** xxx  
**Parameters:**
* `xxx`: xxx

**Returns:**
* `xxx`: xxx

## captureRDT()
**Signature:** `xxx`  
**Purpose:** xxx  
**Parameters:**
* `xxx`: xxx

**Returns:**
* `xxx`: xxx

## enhanceResultWindow()
**Signature:** `xxx`  
**Purpose:** xxx  
**Parameters:**
* `xxx`: xxx

**Returns:**
* `xxx`: xxx

## interpretResult()
**Signature:** `InterpretationResult interpResult = interpretResult(Mat inputMat, MatOfPoint2f boundary)`
**Purpose:** 
**Parameters:**
* `Mat inputMat`: the image known to have a clear RDT in the video frame
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `InterpretationResult interpResult`: 
