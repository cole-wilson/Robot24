package Team4450.Robot24.commands;

import org.photonvision.targeting.PhotonTrackedTarget;

import Team4450.Lib.Util;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.util.sendable.SendableRegistry;
import edu.wpi.first.wpilibj2.command.Command;
import Team4450.Robot24.subsystems.DriveBase;
import Team4450.Robot24.subsystems.PhotonVision;

public class DriveToNote extends Command {
    PIDController rotationController = new PIDController(0.01, 0.005, 0); // for rotating drivebase
    PIDController translationController = new PIDController(0.8, 0, 0); // for moving drivebase in X,Y plane
    DriveBase robotDrive;
    PhotonVision photonVision;
    private boolean alsoDrive;
    private boolean initialFieldRel;

    /**
     * Track to a note using getArea() and getYaw()
     * @param robotDrive the robot drive base
     * @param photonVision the photonvision subsystem
     */
    public DriveToNote(DriveBase robotDrive, PhotonVision photonVision, boolean alsoDrive) {
        this.robotDrive = robotDrive;
        this.photonVision = photonVision;
        this.alsoDrive = alsoDrive;

        SendableRegistry.addLW(translationController, "DriveToNote Translation PID");
        SendableRegistry.addLW(rotationController, "DriveToNote Rotation PID");
    }

    @Override
    public void initialize() {
        Util.consoleLog();

        initialFieldRel = robotDrive.getFieldRelative();
        if (initialFieldRel)
            robotDrive.toggleFieldRelative();

        rotationController.setSetpoint(0); // target should be at yaw=0 degrees
        rotationController.setTolerance(1.5); // withing 0.5 degrees of 0

        translationController.setSetpoint(0); // target should be at -15 pitch
        translationController.setTolerance(0.5);
    }

    @Override
    public void execute() {
        if (!photonVision.hasTargets()) return;

        PhotonTrackedTarget target = photonVision.getClosestTarget();

        // Util.consoleLog("yaw=%f", target.getYaw());
        // Util.consoleLog("pitch=%f", target.getPitch());

        double rotation = rotationController.calculate(target.getYaw());
        double movement = translationController.calculate(target.getPitch());

        // // make sure target centered before we move
        // if (!rotationController.atSetpoint()) {
        if (alsoDrive) {
            robotDrive.driveRobotRelative(-movement, 0, rotation);
        }
        // // otherwise drive to the target (only forwards backwards)
        // if (!translationController.atSetpoint() && alsoDrive) {
        //     robotDrive.driveRobotRelative(-movement, 0, 0); // negative because camera backwards.
        // }
    }
    

    @Override
    public void end(boolean interrupted) {
        Util.consoleLog("interrupted=%b", interrupted);
        if (initialFieldRel)
            robotDrive.getFieldRelative(); // toggle back to beginning state
    }
}