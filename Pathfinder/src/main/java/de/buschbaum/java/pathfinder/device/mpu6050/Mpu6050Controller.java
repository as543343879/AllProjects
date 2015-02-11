/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.buschbaum.java.pathfinder.device.mpu6050;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import de.buschbaum.java.pathfinder.common.Configuration;
import de.buschbaum.java.pathfinder.common.Mathematics;
import de.buschbaum.java.pathfinder.common.Printer;
import de.buschbaum.java.pathfinder.logic.TimingController;
import java.io.IOException;
import java.util.Arrays;

/**
 *
 * @author uli
 */
public class Mpu6050Controller {

    private static I2CBus bus = null;
    private static I2CDevice mpu6050 = null;

    public static void initialize() throws Exception {
        initializeI2C();
        configureMpu6050();
        calibrate();
    }

    public static double[] getAlignedAccelerationValues() throws Exception {
        //Read from Mpu6050
        double[] accVector = new double[3];
        accVector[0] = readAccXRegisters();
        accVector[1] = readAccYRegisters();
        accVector[2] = readAccZRegisters();

        //Apply rotation matrix
//        accVector = Mathematics.multiplyVector3(accVector, Configuration.CALIBRATION_ROTATION_MATRIX);

        //Apply noise reduce
//        accVector = removeNoise(accVector);

        return accVector;
    }

    private static double[] removeNoise(double[] vector) {
        vector[0] = Mathematics.applyThreshold(vector[0], Configuration.CALIBRATION_NOISE_THRESHOLD);
        vector[1] = Mathematics.applyThreshold(vector[1], Configuration.CALIBRATION_NOISE_THRESHOLD);
        return vector;
    }

    private static void calibrate() throws Exception {
        System.out.println("Calibrating rotation matrix...");
        calibrateRotationMatrix();
    }

    private static void calibrateRotationMatrix() throws Exception {
        int sumX = 0;
        int sumY = 0;
        int sumZ = 0;

        //Calculating average misalignment values
        int i;
        for (i = 1; i <= Configuration.CALIBRATION_COUNT; i++) {
            short x = readAccXRegisters();
            short y = readAccYRegisters();
            short z = readAccZRegisters();
            sumX += x;
            sumY += y;
            sumZ += z;
            TimingController.timeSlot(Configuration.CALIBRATION_TIME_SLOT, System.nanoTime());
        }

        //Definining the misaligned vector for the vehicle not moving
        double[] misalignmentVector = new double[3];
        misalignmentVector[0] = sumX / i;
        misalignmentVector[1] = sumY / i;
        misalignmentVector[2] = sumZ / i;

        //Calculating the rotation to the correct values
        Configuration.CALIBRATION_ROTATION_MATRIX = Mathematics.rotationMatrix3(misalignmentVector, Configuration.CALIBRATION_VECTOR);
        System.out.println("Calculated rotation matrix for vector " + Arrays.toString(misalignmentVector) + " is "
                + Arrays.toString(Configuration.CALIBRATION_ROTATION_MATRIX[0]) + ","
                + Arrays.toString(Configuration.CALIBRATION_ROTATION_MATRIX[1]) + ","
                + Arrays.toString(Configuration.CALIBRATION_ROTATION_MATRIX[2]));
    }

    private static short readAccXRegisters() throws IOException {
        short accX = readRegistertsAndShiftWithLsb(Mpu6050Registers.MPU6050_RA_ACCEL_XOUT_L,
                Mpu6050Registers.MPU6050_RA_ACCEL_XOUT_H);
        return accX;
    }

    private static short readAccYRegisters() throws IOException {
        short accY = readRegistertsAndShiftWithLsb(Mpu6050Registers.MPU6050_RA_ACCEL_YOUT_L,
                Mpu6050Registers.MPU6050_RA_ACCEL_YOUT_H);
        return accY;
    }

    private static short readAccZRegisters() throws IOException {
        short accZ = readRegistertsAndShiftWithLsb(Mpu6050Registers.MPU6050_RA_ACCEL_ZOUT_L,
                Mpu6050Registers.MPU6050_RA_ACCEL_ZOUT_H);
        return accZ;
    }

    private static short readRegistertsAndShiftWithLsb(byte lowByteRegister, byte highByteRegister) throws IOException {
        //Read
        byte lowByte = readRegister(lowByteRegister);
        byte highByte = readRegister(highByteRegister);

        //Shift
        short result = Mathematics.shiftBytesTogether(lowByte, highByte);

        return result;
    }

    private static void initializeI2C() throws IOException {
        System.out.println("Creating I2C bus");
        bus = I2CFactory.getInstance(I2CBus.BUS_1);
        System.out.println("Creating I2C device");
        mpu6050 = bus.getDevice(0x68);
    }

    private static void configureMpu6050() throws IOException, InterruptedException {

        //1 Waking the device up
        writeConfigRegisterAndValidate(
                "Waking up device",
                "Wake-up config succcessfully written: ",
                Mpu6050Registers.MPU6050_RA_PWR_MGMT_1,
                Mpu6050RegisterValues.MPU6050_RA_PWR_MGMT_1);

        //2 Configure sample rate
        writeConfigRegisterAndValidate(
                "Configuring sample rate",
                "Sample rate succcessfully written: ",
                Mpu6050Registers.MPU6050_RA_SMPLRT_DIV,
                Mpu6050RegisterValues.MPU6050_RA_SMPLRT_DIV);

        //3 Setting global config
        writeConfigRegisterAndValidate(
                "Setting global config (digital low pass filter)",
                "Global config succcessfully written: ",
                Mpu6050Registers.MPU6050_RA_CONFIG,
                Mpu6050RegisterValues.MPU6050_RA_CONFIG);

        //4 Configure Gyroscope
        writeConfigRegisterAndValidate(
                "Configuring gyroscope",
                "Gyroscope config successfully written: ",
                Mpu6050Registers.MPU6050_RA_GYRO_CONFIG,
                Mpu6050RegisterValues.MPU6050_RA_GYRO_CONFIG);

        //5 Configure Accelerometer
        writeConfigRegisterAndValidate(
                "Configuring accelerometer",
                "Accelerometer config successfully written: ",
                Mpu6050Registers.MPU6050_RA_ACCEL_CONFIG,
                Mpu6050RegisterValues.MPU6050_RA_ACCEL_CONFIG);

        //6 Configure interrupts
        writeConfigRegisterAndValidate(
                "Configuring interrupts",
                "Interrupt config successfully written: ",
                Mpu6050Registers.MPU6050_RA_INT_ENABLE,
                Mpu6050RegisterValues.MPU6050_RA_INT_ENABLE);

        //7 Configure low power operations
        writeConfigRegisterAndValidate(
                "Configuring low power operations",
                "Low power operation config successfully written: ",
                Mpu6050Registers.MPU6050_RA_PWR_MGMT_2,
                Mpu6050RegisterValues.MPU6050_RA_PWR_MGMT_2);
    }

    private static void writeRegister(byte register, byte data) throws IOException {
        mpu6050.write(register, data);
    }

    private static byte readRegister(byte register) throws IOException {
        int data = mpu6050.read(register);
        return (byte) data;
    }

    private static byte readRegister() throws IOException {
        int data = mpu6050.read();
        return (byte) data;
    }

    private static void writeConfigRegisterAndValidate(String initialText, String successText, byte register, byte registerData) throws IOException {
        System.out.println(initialText);
        writeRegister(register, registerData);
        byte returnedRegisterData = Mpu6050Controller.readRegister(register);
        if (returnedRegisterData == registerData) {
            System.out.println(successText + Printer.formatBinary(returnedRegisterData));
        } else {
            throw new RuntimeException("Tried to write " + Printer.formatBinary(registerData) + " to "
                    + register + ", but validiating value returned " + Printer.formatBinary(returnedRegisterData));
        }
    }
}