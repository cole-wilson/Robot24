package Team4450.Robot24.subsystems;

import static Team4450.Robot24.Constants.ELEVATOR_CENTERSTAGE_FACTOR;
import static Team4450.Robot24.Constants.ELEVATOR_MOTOR_INNER;
import static Team4450.Robot24.Constants.ELEVATOR_MOTOR_LEFT;
import static Team4450.Robot24.Constants.ELEVATOR_MOTOR_RIGHT;
import static Team4450.Robot24.Constants.ELEVATOR_WINCH_FACTOR;

import com.revrobotics.CANSparkMax;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkLimitSwitch;
import com.revrobotics.CANSparkBase.IdleMode;
import com.revrobotics.CANSparkLowLevel.MotorType;
import com.revrobotics.SparkLimitSwitch.Type;

import Team4450.Lib.Util;
import Team4450.Robot24.AdvantageScope;
import Team4450.Robot24.Robot;
import edu.wpi.first.math.controller.ArmFeedforward;
import edu.wpi.first.math.controller.ElevatorFeedforward;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.trajectory.TrapezoidProfile.Constraints;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

public class Elevator extends SubsystemBase {
    private CANSparkMax motorMain = new CANSparkMax(ELEVATOR_MOTOR_LEFT, MotorType.kBrushless);
    private CANSparkMax motorFollower = new CANSparkMax(ELEVATOR_MOTOR_RIGHT, MotorType.kBrushless);
    private CANSparkMax motorCenterstage = new CANSparkMax(ELEVATOR_MOTOR_INNER, MotorType.kBrushless);

    // private PIDController mainPID;
    private ProfiledPIDController mainPID;
    private ElevatorFeedforward mainFeedforward;

    private PIDController centerstagePID;

    private SparkLimitSwitch lowerLimitSwitch;
    private SparkLimitSwitch upperLimitSwitch;
  
    private RelativeEncoder mainEncoder;
    private RelativeEncoder followEncoder;
    private RelativeEncoder centerstageEncoder;

    private final double MAIN_TOLERANCE = 1.5;
    private final double CENTERSTAGE_TOLERANCE = 1;

    private final double MAIN_START_COUNTS = 0.11 / ELEVATOR_WINCH_FACTOR;
    private final double CENTERSTAGE_START_COUNTS = 0;

    private double goal = Double.NaN;
    private double centerstageSetpoint = CENTERSTAGE_START_COUNTS;
    

    public Elevator() {
        Util.consoleLog();

        // follower is mirrored and reversed
        // don't change this it's very important as shafts are linked with coupler
        // and will shatter if driven in opposite directions
        motorFollower.follow(motorMain, true);
        motorFollower.setInverted(true);

        motorFollower.setIdleMode(IdleMode.kBrake);
        motorMain.setIdleMode(IdleMode.kBrake);
        motorCenterstage.setIdleMode(IdleMode.kCoast);

        lowerLimitSwitch = motorFollower.getReverseLimitSwitch(Type.kNormallyOpen);
        upperLimitSwitch = motorFollower.getForwardLimitSwitch(Type.kNormallyOpen);

        lowerLimitSwitch.enableLimitSwitch(true);
        upperLimitSwitch.enableLimitSwitch(true);

        mainEncoder = motorMain.getEncoder();
        followEncoder = motorFollower.getEncoder();
        centerstageEncoder = motorCenterstage.getEncoder();

        resetEncoders();

        // mainEncoder.setPositionConversionFactor(-1);
        // followEncoder.setPositionConversionFactor(-1);

        mainPID = new ProfiledPIDController(0.12, 0, 0, new Constraints(200, 1));
        mainFeedforward = new ElevatorFeedforward(1.75, 1.95, 0);

        SmartDashboard.putData("winch_pid", mainPID);
        mainPID.setTolerance(MAIN_TOLERANCE);

        // followerPID = new PIDController(0.01, 0, 0);
        centerstagePID = new PIDController(0.03, 0, 0);
        centerstagePID.setTolerance(CENTERSTAGE_TOLERANCE);
    }

    // private void configurePID(SparkPIDController pidController, double p, double i, double d) {
    //     pidController.setP(p);
    //     pidController.setI(i);
    //     pidController.setD(d);
    // }

    @Override
    public void periodic() {
        // if (lowerLimitSwitch.isPressed()) {
        //     mainEncoder.setPosition(0); // reset the encoder counts
        //     followEncoder.setPosition(0);
        //     centerstageEncoder.setPosition(0);[]
        // }
        SmartDashboard.putNumber("winch_measured", mainEncoder.getPosition());
        SmartDashboard.putNumber("centerstage_measured", centerstageEncoder.getPosition());
        
        AdvantageScope.getInstance().setElevatorHeight(getElevatorHeight());
        AdvantageScope.getInstance().setCarriageHeight(getCenterstageHeight());

        SmartDashboard.putNumber("winch_1_m", mainEncoder.getPosition() * ELEVATOR_WINCH_FACTOR);
        SmartDashboard.putNumber("windh_2_m", followEncoder.getPosition() * ELEVATOR_WINCH_FACTOR);
        SmartDashboard.putNumber("centerstage_m", centerstageEncoder.getPosition() * ELEVATOR_CENTERSTAGE_FACTOR); 
        

        if (Double.isNaN(goal))
            return;
        // elevator main winch PID loop
        SmartDashboard.putNumber("winch_setpoint", goal);
        // mainPID.setSetpoint(goal);
        mainPID.setGoal(goal);

        double nonclamped = mainPID.calculate(mainEncoder.getPosition());
        double feedForward = mainFeedforward.calculate(mainPID.getSetpoint().velocity);

        Util.consoleLog("FF=%f", feedForward);

        double motorOutput = Util.clampValue(nonclamped + feedForward, 1);
        motorMain.set(motorOutput);

        SmartDashboard.putNumber("winch_nonclamped", nonclamped);
        SmartDashboard.putNumber("winch_output", motorOutput);


        if (Robot.isSimulation()) mainEncoder.setPosition(mainEncoder.getPosition() + (1*motorOutput));
        if (Robot.isSimulation()) followEncoder.setPosition(followEncoder.getPosition() + (1*motorOutput));

        // centerstage PID loop
        SmartDashboard.putNumber("centerstage_setpoint", centerstageSetpoint);
        centerstagePID.setSetpoint(centerstageSetpoint);
        motorOutput = centerstagePID.calculate(centerstageEncoder.getPosition());
        motorCenterstage.set(motorOutput);
        if (Robot.isSimulation()) centerstageEncoder.setPosition(centerstageEncoder.getPosition() + (1*motorOutput));
    }

    public void stopMoving() {
        goal = Double.NaN;
    }

    /**
     * move elevator in direction based on speed
     * @param speed (such as from a joystick value)
     */
    public void move(double speed) {
        goal -= speed;
        if (goal < -59)//-59)
            goal = -59;//-59;
        // if (setpoint > -16)
        //     setpoint = -16;
        // if (speed < 0)
        //     speed *= 0.1;
        // speed *= -0.5;
        // motorMain.set(speed);
        // if (Robot.isSimulation()) {
        //     if (speed > 0) speed *= 10;
        //     mainEncoder.setPosition(mainEncoder.getPosition() + (1*speed));
        //     followEncoder.setPosition(followEncoder.getPosition() + (1*speed));
        // }
    }
    public void moveUnsafe(double speed) {
        goal = Double.NaN;
        motorMain.set(speed);
    }

    public void moveCenterStage(double speed) {
        centerstageSetpoint += speed;
        // motorCenterstage.set(speed);
        // if (Robot.isSimulation()) {
        //     centerstageEncoder.setPosition(centerstageEncoder.getPosition() + (1*speed));
        // }
    }

    public void setElevatorHeight(double height) {
        goal = height / ELEVATOR_WINCH_FACTOR; // meters -> enc. counts
    }

    public void setCenterstageHeight(double height) {
        centerstageSetpoint = height / ELEVATOR_CENTERSTAGE_FACTOR; // meters -> enc. counts
    }

    public boolean elevatorIsAtHeight(double height) {
        double setpoint = height / ELEVATOR_WINCH_FACTOR;
        return Math.abs(setpoint - mainEncoder.getPosition()) < MAIN_TOLERANCE;
    }

    public boolean centerstageIsAtHeight(double height) {
        double setpoint = height / ELEVATOR_CENTERSTAGE_FACTOR;
        return Math.abs(setpoint - centerstageEncoder.getPosition()) < CENTERSTAGE_TOLERANCE;
    }

    public double getElevatorHeight() {
        double mainValue = mainEncoder.getPosition() * ELEVATOR_WINCH_FACTOR;
        return mainValue;
        // double followValue = followEncoder.getPosition() * ELEVATOR_WINCH_FACTOR;
        // return (0.5 * (mainValue + followValue)); // mean
    }

    public double getCenterstageHeight() {return centerstageEncoder.getPosition() * ELEVATOR_CENTERSTAGE_FACTOR;}

    public void resetEncoders() {
        mainEncoder.setPosition(MAIN_START_COUNTS);
        followEncoder.setPosition(MAIN_START_COUNTS);
        goal = MAIN_START_COUNTS;
        centerstageEncoder.setPosition(0);
    }

    public void lockPosition() {
        goal = mainEncoder.getPosition();
    }

    // /**
    //  * The height of the elevator (measured at shooter pivot)
    //  * above the ground
    //  * @return the height in meters of MAXSpline shaft above ground
    //  */
    // public double getHeight() {
    //     double avgCounts = 0.5 * (mainEncoder.getPosition() + followEncoder.getPosition());
    //     return avgCounts;
    // }

}
