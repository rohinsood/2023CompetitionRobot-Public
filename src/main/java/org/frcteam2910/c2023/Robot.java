// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package org.frcteam2910.c2023;

import edu.wpi.first.math.filter.LinearFilter;
import edu.wpi.first.wpilibj.PowerDistribution;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import org.frcteam2910.c2023.config.RobotIdentity;
import org.frcteam2910.c2023.subsystems.led.LEDSubsystem;
import org.frcteam2910.c2023.util.DriverReadouts;
import org.frcteam2910.c2023.util.GamePiece;
import org.frcteam2910.c2023.util.MacAddressUtil;
import org.frcteam2910.c2023.util.constants.FieldConstants;
import org.littletonrobotics.junction.LogFileUtil;
import org.littletonrobotics.junction.LoggedRobot;
import org.littletonrobotics.junction.Logger;
import org.littletonrobotics.junction.networktables.NT4Publisher;
import org.littletonrobotics.junction.wpilog.WPILOGReader;
import org.littletonrobotics.junction.wpilog.WPILOGWriter;

public class Robot extends LoggedRobot {
    private RobotContainer robotContainer;

    private final PowerDistribution powerDistribution = new PowerDistribution();
    private final LinearFilter average = LinearFilter.movingAverage(50);

    @Override
    public void robotInit() {
        // Record metadata
        Logger.recordMetadata("GitRevision", String.valueOf(BuildInfo.GIT_REVISION));
        Logger.recordMetadata("GitSHA", BuildInfo.GIT_SHA);
        Logger.recordMetadata("GitDate", BuildInfo.GIT_DATE);
        Logger.recordMetadata("GitBranch", BuildInfo.GIT_BRANCH);
        Logger.recordMetadata("BuildDate", BuildInfo.BUILD_DATE);
        Logger.recordMetadata("BuildUnixTime", String.valueOf(BuildInfo.BUILD_UNIX_TIME));
        switch (BuildInfo.DIRTY) {
            case 0:
                Logger.recordMetadata("GitDirty", "Clean");
                break;
            case 1:
                Logger.recordMetadata("GitDirty", "Dirty");
                break;
            default:
                Logger.recordMetadata("GitDirty", "Error");
                break;
        }
        Logger.recordMetadata("Robot Name", RobotIdentity.getIdentity().toString());
        Logger.recordMetadata("Robot MAC Address", MacAddressUtil.getMACAddress());

        // Set up data receivers & replay sources
        switch (RobotConfiguration.getMode()) {
                // Running on a real robot, log to a USB stick
            case REAL:
                Logger.addDataReceiver(new WPILOGWriter("/media/sda1/"));
                Logger.addDataReceiver(new NT4Publisher());
                break;

                // Running a physics simulator, log to NetworkTables
            case SIM:
                Logger.addDataReceiver(new NT4Publisher());
                break;

                // Replaying a log, set up replay source
            case REPLAY:
                setUseTiming(false); // Run as fast as possible
                String logPath = LogFileUtil.findReplayLog();
                Logger.setReplaySource(new WPILOGReader(logPath));
                Logger.addDataReceiver(new WPILOGWriter(LogFileUtil.addPathSuffix(logPath, "_sim")));
        }

        // Start AdvantageKit logger
        Logger.start();

        robotContainer = new RobotContainer();
    }

    @SuppressWarnings("unused")
    private final DriverReadouts driverReadout = new DriverReadouts(robotContainer);

    @Override
    public void robotPeriodic() {
        double start = System.currentTimeMillis();
        CommandScheduler.getInstance().run();
        double end = System.currentTimeMillis();
        double commandSchedulerTime = end - start;
        Logger.recordOutput("Times/CommandSchedulerMs", commandSchedulerTime);
        Logger.recordOutput("Times/CommandSchedulerMsAverage", average.calculate(commandSchedulerTime));

        start = System.currentTimeMillis();
        robotContainer.getOperatorDashboard().update();
        end = System.currentTimeMillis();
        Logger.recordOutput("Times/OperatorDashboardMs", end - start);

        start = System.currentTimeMillis();
        robotContainer.getSuperstructure().update();
        end = System.currentTimeMillis();
        Logger.recordOutput("Times/SuperstructureMs", end - start);

        start = System.currentTimeMillis();
        FieldConstants.update();
        end = System.currentTimeMillis();
        Logger.recordOutput("Times/FieldConstantsMs", end - start);
    }

    @Override
    public void disabledPeriodic() {
        if (robotContainer.getArmSubsystem().isZeroed()) {
            robotContainer.getLedSubsystem().setWantedAction(LEDSubsystem.WantedAction.DISPLAY_ROBOT_ARM_ZEROED);
        } else {
            robotContainer.getLedSubsystem().setWantedAction(LEDSubsystem.WantedAction.DISPLAY_ROBOT_ARM_NOT_ZEROED);
        }
    }

    @Override
    public void teleopPeriodic() {
        if (robotContainer.getArmSubsystem().isZeroed()
                && !robotContainer.getLedSubsystem().getIsBadAlign()
                && !robotContainer.getLedSubsystem().getIsFlashing()) {
            if (robotContainer.getOperatorDashboard().getSelectedGamePiece() == GamePiece.CONE) {
                robotContainer.getLedSubsystem().setWantedAction(LEDSubsystem.WantedAction.DISPLAY_GETTING_CONE);
            } else {
                robotContainer.getLedSubsystem().setWantedAction(LEDSubsystem.WantedAction.DISPLAY_GETTING_CUBE);
            }
        }
    }

    @Override
    public void autonomousInit() {
        robotContainer.getAutonomousCommand().schedule();
    }
}
