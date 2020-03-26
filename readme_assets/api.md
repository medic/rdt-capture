# Classes & Enums
* [`RDT`](#RDT)
* [`ExposureResult`](#exposureResult)
* [`SizeResult`](#sizeResult)
* [`CaptureResult`](#captureResult)
* [`InterpretationResult`](#interpretationResult)

# Methods for RDT Detection
* [`configureCamera()`](#configureCamera)
* [`detectRDT()`](#detectRDT)
* [`captureRDT()`](#captureRDT)

# Methods for Quality Checking
* [`calculateBrightness()`](#calculateBrightness)
* [`checkBrightness()`](#checkBrightness)
* [`calculateSharpness()`](#calculateSharpness)
* [`checkSharpness()`](#checkSharpness)
* [`measureCentering()`](#measureCentering)
* [`checkIfCentered()`](#checkIfCentered)
* [`measureSize()`](#measureSize)
* [`checkSize()`](#checkSize)
* [`measureOrientation()`](#measureOrientation)
* [`checkOrientation()`](#checkOrientation)
* [`checkFiducial()`](#checkFiducial)

# Methods for RDT Interpretation
* [`cropResultWindow()`](#cropResultWindow)
* [`enhanceResultWindow()`](#enhanceResultWindow)
* [`interpretRDT()`](#interpretRDT)

- - -

## RDT
**Signature:** `RDT(Context context, String rdtName)`  
**Purpose:** Object for holding all of the parameters that are loaded from the configuration file for the RDT of interest  
**Parameters:**
* `Context context`: the `Context` object for the app's `Activity` 
* `String rdtName`: the `String` used to reference the RDT design in `config.json`

## ExposureResult
**Signature:** `enum ExposureResult`  
**Purpose:** An `Enumeration` object for specifying the exposure quality of the image  
**Possible Values:**
* `UNDER_EXPOSED`: the image is too dark
* `NORMAL`: the image has just the right amount of light
* `OVER_EXPOSED`: the image is too bright

## SizeResult
**Signature:** `enum SizeResult`  
**Purpose:** An `Enumeration` object for specifying whether the RDT has a reasonable scale in the image  
**Possible Values:**
* `SMALL`: the RDT is too small in the image
* `RIGHT_SIZE`: the RDT has just the right size in the image
* `LARGE`: the RDT is too large in the image
* `INVALID`: the RDT could not be found in the image

## CaptureResult
**Signature:** `CaptureResult(boolean allChecksPassed, Mat resultMat, boolean fiducial, ExposureResult exposureResult, SizeResult sizeResult, boolean isCentered, boolean isRightOrientation, double angle, boolean isSharp, boolean isShadow, MatOfPoint2f boundary, boolean flashEnabled)`  
**Purpose:** Object for holding all of the parameters that describe whether a candidate video framed passed all of the quality checks  
**Parameters:**
* `boolean allChecksPassed`: xxx
* `Mat resultMat`: xxx
* `boolean fiducial`: whether the fiducial was detected (if one was specified)
* `ExposureResult exposureResult`: xxx
* `SizeResult sizeResult`: xxx
* `boolean isCentered`: xxx
* `boolean isRightOrientation`: xxx
* `double angle`: xxx
* `boolean isSharp`: xxx
* `boolean isShadow`: xxx
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT
* `boolean flashEnabled`: xxx

## InterpretationResult
**Signature:** `InterpretationResult(Mat resultMat, boolean topLine, boolean middleLine, boolean bottomLine)`  
**Purpose:** Object for holding all of the parameters that describe the test result that is detected on the completed RDT  
**Parameters:**
* `Mat resultMat`: the RDT image tightly cropped around the result window
* `boolean topLine`: whether the top line was detected in the result window
* `boolean middleLine`: whether the middle line was detected in the result window
* `boolean bottomLine`: whether the bottom line was detected in the result window

- - -

## configureCamera()
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

- - -

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

## measureCentering()
**Signature:** `Point center = measureCentering(MatOfPoint2f boundary)`  
**Purpose:** Identifies the center of the detectedRDT  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `Point center`: the (x, y) coordinate indicating the center of the RDT

## checkIfCentered()
**Signature:** `boolean isCentered = checkIfCentered(MatOfPoint2f boundary, Size size)`  
**Purpose:** Determines whether the RDT is close enough towards the center of the candidate video frame  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT
* `Size size`: the size of the candidate video frame

**Returns:**
* `boolean isCentered`: xxx

## measureSize()
**Signature:** `double height = measureSize(MatOfPoint2f boundary)`  
**Purpose:** xxx  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `double height`: xxx

## checkSize()
**Signature:** `SizeResult sizeResult = checkSize(MatOfPoint2f boundary, Size size)`  
**Purpose:** xxx  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT
* `Size size`: the size of the candidate video frame

**Returns:**
* `SizeResult sizeResult`: xxx

## measureOrientation()
**Signature:** `double angle = measureOrientation(MatOfPoint2f boundary)`  
**Purpose:** xxx  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `double angle`: xxx

## checkOrientation()
**Signature:** `double isOriented = checkOrientation(MatOfPoint2f boundary)`  
**Purpose:** xxx  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `boolean isOriented`: xxx

## checkFiducial()
**Signature:** `xxx`  
**Purpose:** xxx  
**Parameters:**
* `xxx`: xxx

**Returns:**
* `xxx`: xxx

- - -

## cropResultWindow()
**Signature:** `Mat resultWindow = cropResultWindow(Mat inputMat, MatOfPoint2f boundary)`  
**Purpose:** xxx  
**Parameters:**
* `Mat inputMat`: the candidate video frame
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `Mat resultWindow`: the RDT image tightly cropped around the result window


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
