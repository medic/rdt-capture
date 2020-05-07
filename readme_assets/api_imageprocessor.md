# Classes & Enums
* [`RDT`](#RDT)
* [`ExposureResult`](#exposureResult)
* [`SizeResult`](#sizeResult)
* [`RDTCaptureResult`](#rdtCaptureResult)
* [`RDTInterpretationResult`](#rdtInterpretationResult)

# Methods for RDT Detection
* [`getViewfinderRect()`](#getViewfinderRect)
* [`assessImage()`](#assessImage)
* [`detectRDT()`](#detectRDT)
* [`drawKeypointsAndMatches()`](#drawKeypointsAndMatches)

# Methods for Quality Checking
* [`measureExposure()`](#measureExposure)
* [`checkExposure()`](#checkExposure)
* [`measureSharpness()`](#measureSharpness)
* [`checkSharpness()`](#checkSharpness)
* [`measurePosition()`](#measurePosition)
* [`checkPosition()`](#checkPosition)
* [`measureSize()`](#measureSize)
* [`checkSize()`](#checkSize)
* [`measureOrientation()`](#measureOrientation)
* [`checkOrientation()`](#checkOrientation)
* [`checkGlare()`](#checkGlare)
* [`getInstructionText()`](#getInstructionText)
* [`getSummaryText()`](#getSummaryText)

# Methods for RDT Interpretation
* [`checkBlood()`](#checkBlood)
* [`cropResultWindow()`](#cropResultWindow)
* [`cropResultWindowWithFiducial()`](#checkFiducial)
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
**Signature:** `RDTCaptureResult(boolean allChecksPassed, Mat resultMat, MatOfPoint2f boundary, boolean flashEnabled, ExposureResult exposureResult, boolean isSharp,  boolean isCentered, SizeResult sizeResult, boolean isOriented, double angle, boolean isGlared, boolean fiducial)`  
**Purpose:** Object for holding all of the parameters that describe whether a candidate video framed passed all of the quality checks  
**Parameters:**
* `boolean allChecksPassed`: whether this candidate video frame is clear enough for interpretation
* `Mat resultMat`: the RDT image tightly cropped around the result window
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT
* `boolean flashEnabled`: whether the flash was active during the image capture process for this frame
* `ExposureResult exposureResult`: whether the candidate video frame `input` has a reasonable brightness
* `boolean isSharp`: whether the candidate video frame `input` has a reasonable sharpness
* `boolean isCentered`: whether the `boundary` of the detected RDT is sufficiently in the middle of the screen for consistent interpretation
* `SizeResult sizeResult`: whether the `boundary` of the detected RDT has a reasonable size for consistent interpretation
* `boolean isOriented`: whether the `boundary` of the detected RDT has a reasonable orientation for consistent interpretation
* `double angle`: the orientation of the RDT's vertical axis relative to the vertical axis of the video frame (e.g., 0&deg; = upright, 90&deg; = right-to-left, 180&deg; = upside-down, 270&deg; = left-to-right)
* `boolean isGlared`: whether there is glare within the detected RDT's result window
* `boolean fiducial`: whether the fiducial was detected (if one was specified)

## RDTInterpretationResult
**Signature:** `RDTInterpretationResult(Mat resultMat, boolean topLine, boolean middleLine, boolean bottomLine)`  
**Purpose:** Object for holding all of the parameters that describe the test result that is detected on the completed RDT  
**Parameters:**
* `Mat resultMat`: the RDT image tightly cropped around the result window
* `boolean topLine`: whether the top line was detected in the result window
* `boolean middleLine`: whether the middle line was detected in the result window
* `boolean bottomLine`: whether the bottom line was detected in the result window

- - -

## getViewfinderRect()
**Signature:** `Rect viewfinderRect = getViewfinderRect(Mat inputMat)`  
**Purpose:** Returns the rectangle corresponding to the viewfinder that the user sees (i.e., region-of-interest) for image quality  
**Parameters:**
* `Mat inputMat`: the candidate video frame

**Returns:**
* `Rect viewfinderRect`: the viewfinder rectangle

## assessImage()
**Signature:** `RDTCaptureResult captureResult = assessImage(Mat inputMat, boolean flashEnabled)`  
**Purpose:** Processes the candidate video frame to see if it passes all of the quality checks needed to ensure high likelihood of correct automatic analysis  
**Parameters:**
* `Mat inputMat`: the candidate video frame (in RGBA)
* `boolean flashEnabled`: whether the flash was active during the image capture process for this frame

**Returns:**
* `RDTCaptureResult captureResult`: the capture results

## detectRDT()
**Signature:** `MatOfPoint2f boundary = detectRDT(Mat inputMat)`  
**Purpose:** Locates the RDT within the image (if one is presents) produces a bounding box around it  
**Parameters:**
* `Mat inputMat`: the candidate video frame (in grayscale)

**Returns:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT if one is present, otherwise a blank MatOfPoint2f

## drawKeypointsAndMatches()
**Signature:** `Mat debugMat = drawKeypointsAndMatches(Mat inputMat, MatOfPoint boundary, MatOfKeyPoint keypoints, MatOfDMatch matchesMat)`  
**Purpose:** Draws an image for debugging the feature-based template matching approach for RDT detection  
**Parameters:**
* `Mat inputMat`: the candidate video frame (in grayscale)
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT
* `MatOfKeyPoint keypoints`: the SIFT keypoints within the video frame
* `MatOfDMatch matchesMat`: the correspondence between the different keypoints

**Returns:**
* `Mat debugMat`: an image with keypoint matches drawn between the reference image and the candidate video frame

- - -

## measureExposure()
**Signature:** `float[] mBuff = measureExposure(Mat inputMat)`  
**Purpose:** Calculates the brightness histogram of the candidate video frame  
**Parameters:**
* `Mat inputMat`: the candidate video frame (in grayscale)

**Returns:**
* `float[] mBuff`: a 256-element histogram that quantifies the number of pixels at each brightness level for the greyscale version of `inputMat`

## checkExposure()
**Signature:** `ExposureResult exposureResult = checkExposure(Mat inputMat)`  
**Purpose:** Determines whether the candidate video frame has sufficient lighting without being too bright  
**Parameters:**
* `Mat inputMat`: the candidate video frame (in grayscale)

**Returns:**
* `ExposureResult exposureResult`: whether `inputMat` has a reasonable brightness

### measureSharpness()
**Signature:** `double sharpness = measureSharpness(Mat inputMat)`  
**Purpose:** Calculates the Laplacian variance of the candidate video frame as a metric for sharpness  
**Parameters:**
* `Mat inputMat`: the candidate video frame (in grayscale)

**Returns:**
* `double sharpness`: the Laplacian variance of `inputMat`

## checkSharpness()
**Signature:** `boolean isSharp = checkSharpness(Mat inputMat)`  
**Purpose:** Determines whether the candidate video frame is focused  
**Parameters:**
* `Mat inputMat`: the candidate video frame (in grayscale)

**Returns:**
* `boolean isSharp`: whether `inputMat` has a reasonable sharpness

## measurePosition()
**Signature:** `Point center = measurePosition(MatOfPoint2f boundary)`  
**Purpose:** Identifies the center of the detected RDT  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `Point center`: the (x, y) coordinate corresponding to the center of the RDT

## checkPosition()
**Signature:** `boolean isCentered = checkPosition(MatOfPoint2f boundary, Size size)`  
**Purpose:** Determines whether the detected RDT is close enough towards the center of the candidate video frame  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT
* `Size size`: the size of the candidate video frame

**Returns:**
* `boolean isCentered`: whether the `boundary` of the detected RDT is sufficiently in the middle of the screen for consistent interpretation

## measureSize()
**Signature:** `double dimension = measureSize(MatOfPoint2f boundary, boolean isHeight)`  
**Purpose:** Measures the desired dimension of the bounding box around the detected RDT  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT
* `boolean isHeight`: whether the output should be the height (true) or width (false)

**Returns:**
* `double dimension`: the desired dimension in pixels

## checkSize()
**Signature:** `SizeResult sizeResult = checkSize(MatOfPoint2f boundary, Size size)`  
**Purpose:** Determines whether the detected RDT is a reasonable size within the camera frame  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT
* `Size size`: the size of the candidate video frame

**Returns:**
* `SizeResult sizeResult`: whether the `boundary` of the detected RDT has a reasonable size for consistent interpretation

## measureOrientation()
**Signature:** `double angle = measureOrientation(MatOfPoint2f boundary)`  
**Purpose:** Measures the orientation of the RDT relative to the camera's perspective (assumes vertical RDT where height > width)  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `double angle`: the orientation of the RDT's vertical axis relative to the vertical axis of the video frame (0&deg; = upright, 90&deg; = right-to-left, 180&deg; = upside-down, 270&deg; = left-to-right) 

## checkOrientation()
**Signature:** `double isOriented = checkOrientation(MatOfPoint2f boundary)`  
**Purpose:** Determines whether the detected RDT is a reasonable orientation within the camera frame  
**Parameters:**
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `boolean isOriented`: whether the `boundary` of the detected RDT has a reasonable orientation for consistent interpretation

## checkGlare()
**Signature:** `boolean isGlared = checkIfGlared(Mat inputMat, MatOfPoint2f boundary)`  
**Purpose:** Determines if there is glare within the detected RDT's result window (often due to protective covering of the immunoassay)  
**Parameters:**
* `Mat inputMat`: the candidate video frame (in grayscale)
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `boolean isGlared`: whether there is glare within the detected RDT's result window

## getInstructionText()
**Signature:** `int instruction = getInstructionText(boolean isCentered, SizeResult sizeResult, boolean isOriented, boolean isGlared)`  
**Purpose:** Generate the most logical instruction to help the user fix a single quality check  
**Parameters:**
* `boolean isCentered`: whether the `boundary` of the detected RDT is sufficiently in the middle of the screen for consistent interpretation
* `SizeResult sizeResult`: whether the `boundary` of the detected RDT has a reasonable size for consistent interpretation
* `boolean isOriented`: whether the `boundary` of the detected RDT has a reasonable orientation for consistent interpretation
* `boolean isGlared`: whether there is glare within the detected RDT's result window

**Returns:**
* `int instruction`: the ID of the instruction text to be found in `res/values/strings.xml`

## getSummaryText()
**Signature:** `String[] summaryText = String[] getSummaryText(ExposureResult exposureResult, boolean isSharp, boolean isCentered, SizeResult sizeResult, boolean isOriented, boolean isGlared)`  
**Purpose:** Generate text that can be shown on the screen to summarize all quality checks  
**Parameters:**
* `ExposureResult exposureResult`: whether the candidate video frame has a reasonable brightness
* `boolean isSharp`: whether the candidate video frame has a reasonable sharpness
* `boolean isCentered`: whether the `boundary` of the detected RDT is sufficiently in the middle of the screen for consistent interpretation
* `SizeResult sizeResult`: whether the `boundary` of the detected RDT has a reasonable size for consistent interpretation
* `boolean isOriented`: whether the `boundary` of the detected RDT has a reasonable orientation for consistent interpretation
* `boolean isGlared`: whether there is glare within the detected RDT's result window

**Returns:**
* `String[] summaryText`: summary for each quality checking component

- - -

## checkBlood()
**Signature:** `boolean isBloody = checkBloody(Mat inputMat, MatOfPoint2f boundary)`  
**Purpose:** Determines if there is blood within the detected RDT's result window  
**Parameters:**
* `Mat inputMat`: the candidate video frame (in grayscale)
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `boolean isBloody`: whether there is blood within the detected RDT's result window

## cropResultWindow()
**Signature:** `Mat resultWindow = cropResultWindow(Mat inputMat, MatOfPoint2f boundary)`  
**Purpose:** Crops out the detected RDT's result window as a rectangle  
**Parameters:**
* `Mat inputMat`: the candidate video frame
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `Mat resultWindow`: the RDT image tightly cropped and de-skewed around the result window

## cropResultWindowWithFidicual()
**Signature:** `Mat resultWindow = cropResultWindowWithFidicual(Mat inputMat)`  
**Purpose:** Uses color clustering to identify explicit 'fiducials' (densely colored markers) on the detected RDT that can be used as reference points for locating the result window  
**Parameters:**
* `Mat inputMat`: the candidate video frame (in RGBA and de-skewed)

**Returns:**
* `Mat resultWindow`: the RDT image tightly cropped and de-skewed around the result window

## enhanceResultWindow()
**Signature:** `Mat enhancedMat = Mat enhanceResultWindow(Mat resultWindowMat)`  
**Purpose:** Applies [CLAHE](https://en.wikipedia.org/wiki/Adaptive_histogram_equalization) to enhance faint marks on the RDT's result window  
**Parameters:**
* `Mat resultWindowMat`: the RDT's result window (in RGBA)

**Returns:**
* `Mat enhancedMat`: a contrast-enhanced version of the RDT's result window

## interpretRDT()
**Signature:** `InterpretationResult interpResult = interpretRDT(Mat inputMat, MatOfPoint2f boundary)`  
**Purpose:** Interprets any lines that appear within the detected RDT's result window  
**Parameters:**
* `Mat inputMat`: the image known to have a clear RDT in the video frame
* `MatOfPoint2f boundary`: the corners of the bounding box around the detected RDT

**Returns:**
* `InterpretationResult interpResult`: the test results
