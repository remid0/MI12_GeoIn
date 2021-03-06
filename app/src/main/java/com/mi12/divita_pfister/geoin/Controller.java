package com.mi12.divita_pfister.geoin;


/**
 * Central class that gather all sensors declaration
 */
public class Controller {
    public static float STEP_DISTANCE = 0.75f;
    public int MAX_HISTORY = 100;
    public int RESET_INTERVAL = 10;

    public MapsActivity display;
    public GpsSensor gps;
    private AccelerometerSensor accelerometer;
    private OrientationSensor orientation;

    private int historyPointer =0;
    private HistoryValue[] history = new HistoryValue[MAX_HISTORY];
    private int stepCounter = 0;

    // Calibration
    private boolean isCalibrated;
    private int firstPointer = -1, calibrationCounter = 0;
    private int MAX_STEP_CALIBRATION = 50, MIN_STEP_CALIBRATION = 20;
    private int STEP_CALIBRATION_TIME_THRESHOLD = 10;
    private int STEP_CALIBRATION_ACCURACY_THRESHOLD = 10;

    /**
     * Constructor
     * @param display is the main class in order to be able to display information
     */
    public Controller(MapsActivity display){
        this.display = display;
        this.gps = new GpsSensor(this);
        this.accelerometer = new AccelerometerSensor(this);
        this.orientation = new OrientationSensor(this.display);
    }

    /**
     * The method records a new gpsValue and stepPosition every time it is needed
     * @param reset every 10 values recorded, in outdoor mode only, we set the StepPosition to the
     *              gpsValue counter draft
     * @param isIndoorMode is true when the detected mode is Indoor
     * @param gpsValue recorded gps value
     * @param timestamp timestamp associated with the recorded value
     * @return true if we recorded a new value, false if not
     */
    private boolean recordHistoryValue (
            boolean reset, boolean isIndoorMode, GpsValue gpsValue, long timestamp
    ) {
        if (history[(historyPointer - 1 + MAX_HISTORY) % MAX_HISTORY] == null) {
            reset = true;
            if(isIndoorMode) {
                return false;
            }
        }
        StepPosition stepPosition;
        if(reset && !isIndoorMode) {
            stepPosition = new StepPosition(
                    new double[]{gpsValue.getLatitude(), gpsValue.getLongitude()},
                    timestamp
            );
        } else {
            stepPosition = Position.addStepDistance(
                    history[(historyPointer - 1 + MAX_HISTORY) % MAX_HISTORY].stepPosition,
                    STEP_DISTANCE,
                    orientation.getOrientationAngles().getAzimuth(),
                    timestamp
            );
        }
        history[historyPointer] = new HistoryValue(
                stepPosition,
                gpsValue,
                isIndoorMode
        );
        return true;
    }

    /**
     * Action when we detect a step
     * @param timestamp time associated with the step detection
     */
    public void onStepDetected(long timestamp){
        if(gps.isReady()) {
            GpsValue gpsValue = gps.getLastPosition();
            boolean isIndoorMode = gps.getIndoorMode();
            boolean reset = false;
            if (
                    history[(historyPointer - 1 + MAX_HISTORY) % MAX_HISTORY] != null &&
                    history[(historyPointer - 1 + MAX_HISTORY) % MAX_HISTORY].isIndoorMode &&
                    isIndoorMode
                    ){
                reset = true;
            }
            if(!isIndoorMode && historyPointer % RESET_INTERVAL == 0){
                reset = true;
            }
            if(recordHistoryValue(reset, isIndoorMode, gpsValue, timestamp)) {
                if (isIndoorMode) {
                    display.setUserPosition(history[historyPointer].stepPosition);
                } else {
                    display.setUserPosition(history[historyPointer].gpsPosition);
                }
                if(!isCalibrated) calibrate();
                historyPointer = (historyPointer + 1) % MAX_HISTORY;
            }
            stepCounter++;
            display.setStepCounterLabel(stepCounter);
        }
    }

    /**
     * Use the history of position to calibrate the user step distance.
     */
    private void calibrate(){
        if(calibrationCounter > 0) calibrationCounter++;
        boolean isGoodGpsValue = history[historyPointer].gpsPosition.getDatetime() -
                history[historyPointer].stepPosition.getDatetime() <= STEP_CALIBRATION_TIME_THRESHOLD
                && history[historyPointer].gpsPosition.getAccuracy() <= STEP_CALIBRATION_ACCURACY_THRESHOLD;
        if(firstPointer == -1 && isGoodGpsValue){
            firstPointer = historyPointer;
            calibrationCounter = 1;
        } else {
            if(calibrationCounter >= MIN_STEP_CALIBRATION && calibrationCounter <= MAX_STEP_CALIBRATION )
                if (isGoodGpsValue){
                {
                    STEP_DISTANCE = (float) Position.calculateDistance(history[firstPointer].gpsPosition, history[historyPointer].gpsPosition)/calibrationCounter;
                    isCalibrated = true;
                }
            }
            else {
                if (calibrationCounter > MAX_STEP_CALIBRATION) {
                    if (isGoodGpsValue) {
                        firstPointer = historyPointer;
                        calibrationCounter = 1;
                    }
                    else{
                        firstPointer = -1;
                        calibrationCounter = 0;
                    }
                }
            }
        }
    }

    /**
     * Function that is trigger when the GpsSensor is ready. It set the user position a first time
     * without requiring stepdetection
     * @param firstPosition is the fiert position detected by the GpsSensor
     */
    public void onGpsReady(GpsValue firstPosition){
        display.setUserPosition(firstPosition);
    }

    /**
     * Method that sets the step counter at the specified value
     * @param value to which the step counter is set
     */
    public void setStepCounter(int value){this.stepCounter = value;}

    /**
     * Get the number of steps since last reset
     * @return stepCounter
     */
    public int getStepCounter(){return this.stepCounter;}
}
