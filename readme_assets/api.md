# Classes & Enums
* [`RDT`](#RDT)
* [`ExposureResult`](#exposureResult)
* [`SizeResult`](#sizeResult)
* [`RDTCaptureResult`](#rdtCaptureResult)
* [`RDTInterpretationResult`](#rdtInterpretationResult)

# Methods for RDT Detection
* [`configureCamera()`](#configureCamera)
* [`assessImage()`](#assessImage)
* [`detectRDT()`](#detectRDT)

# Methods for Quality Checking
* [`measureExposure()`](#measureExposure)
* [`checkExposure()`](#checkExposure)
* [`measureSharpness()`](#measureSharpness)
* [`checkSharpness()`](#checkSharpness)
* [`measureCentering()`](#measureCentering)
* [`checkIfCentered()`](#checkIfCentered)
* [`measureSize()`](#measureSize)
* [`checkSize()`](#checkSize)
* [`measureOrientation()`](#measureOrientation)
* [`checkOrientation()`](#checkOrientation)
* [`checkIfGlared()`](#checkIfGlared)
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

## RDTCaptureResult
**Signature:** `RDTCaptureResult(boolean allChecksPassed, Mat resultMat, boolean fiducial, ExposureResult exposureResult, SizeResult sizeResult, boolean isCentered, boolean isRightOrientation, double angle, boolean isSharp, boolean isShadow, MatOfPoint2f boundary, boolean flashEnabled)`  
**Purpose:** Object for holding all of the parameters that describe whether a candidate video framed passed all of the quality checks  
**Parameters:**
* `boolean allChecksPassed`: whether this candidate video frame is clear enough for interpretation
* `Mat resultMat`: the RDT image tightly cropped around the result window
* `boolean fiducial`: whether the fiducial was detected (if one was specified)
* `ExposureResult exposureResult`: whether the candidate video frame `input` has a reasonable brightness
* `SizeResult sizeResult`: whether the `boundary` of the detected RDT has a reasonable size for consistent interpretation
* `boolean isCentered`: whether the `boundary` of the detected RDT is sufficiently in the middle of the screen for consistent interpretation
* `boolean isRightOrientation`: whether the `boundary` of the detected RDT has a reasonable orientation for consistent interpretation
* `double angle`: the orientation of the RDT's vertical axis relative to the vertical axis of the video frame (e.g., 0&deg; = upright, 90&deg; = right-to-left, 180&deg; = upside-down, 270&deg; = left-to-right)
* `boolean isSharp`: whether the candidate video frame `input` has a reasonable sharpness
* `boolean isShadow`: TODO
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT
* `boolean flashEnabled`: whether the flash was active during the image capture process for this frame

## RDTInterpretationResult
**Signature:** `RDTInterpretationResult(Mat resultMat, boolean topLine, boolean middleLine, boolean bottomLine)`  
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

## assessImage()
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

- - -

## measureExposure()
**Signature:** `float[] mBuff = measureExposure(Mat input)`  
**Purpose:** Calculates the brightness histogram of the candidate video frame  
**Parameters:**
* `Mat input`: the candidate video frame (in grayscale)

**Returns:**
* `float[] mBuff`: a 256-element histogram that quantifies the number of pixels at each brightness level for the greyscale version of `input`

## checkExposure()
**Signature:** `ExposureResult exposureResult = checkExposure(Mat input)`  
**Purpose:** Determines whether the candidate video frame has sufficient lighting without being too bright  
**Parameters:**
* `Mat input`: the candidate video frame (in grayscale)

**Returns:**
* `ExposureResult exposureResult`: whether the candidate video frame `input` has a reasonable brightness

### measureSharpness()
**Signature:** `double sharpness = measureSharpness(Mat input)`  
**Purpose:** Calculates the Laplacian variance of the candidate video frame  
**Parameters:**
* `Mat input`: the candidate video frame (in grayscale)

**Returns:**
* `double sharpness`: the Laplacian variance of `input`

## checkSharpness()
**Signature:** `boolean isSharp = checkSharpness(Mat input)`  
**Purpose:** Determines whether the candidate video frame is focused  
**Parameters:**
* `Mat input`: the candidate video frame (in grayscale)

**Returns:**
* `boolean isSharp`: whether the candidate video frame `input` has a reasonable sharpness

## measureCentering()
**Signature:** `Point center = measureCentering(MatOfPoint2f boundary)`  
**Purpose:** Identifies the center of the detectedRDT  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `Point center`: the (x, y) coordinate corresponding to the center of the RDT

## checkIfCentered()
**Signature:** `boolean isCentered = checkIfCentered(MatOfPoint2f boundary, Size size)`  
**Purpose:** Determines whether the RDT is close enough towards the center of the candidate video frame  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT
* `Size size`: the size of the candidate video frame

**Returns:**
* `boolean isCentered`: whether the `boundary` of the detected RDT is sufficiently in the middle of the screen for consistent interpretation

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
* `SizeResult sizeResult`: whether the `boundary` of the detected RDT has a reasonable size for consistent interpretation

## measureOrientation()
**Signature:** `double angle = measureOrientation(MatOfPoint2f boundary)`  
**Purpose:** xxx  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `double angle`: the orientation of the RDT's vertical axis relative to the vertical axis of the video frame (0&deg; = upright, 90&deg; = right-to-left, 180&deg; = upside-down, 270&deg; = left-to-right) 

## checkOrientation()
**Signature:** `double isOriented = checkOrientation(MatOfPoint2f boundary)`  
**Purpose:** xxx  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `boolean isOriented`: whether the `boundary` of the detected RDT has a reasonable orientation for consistent interpretation

## checkIfGlared()
**Signature:** `boolean isGlared = checkIfGlared(Mat inputMat, MatOfPoint2f boundary)`  
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
