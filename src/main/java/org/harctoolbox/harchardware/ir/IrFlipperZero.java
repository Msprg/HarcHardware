package org.harctoolbox.harchardware.ir;

import org.harctoolbox.harchardware.HarcHardwareException;
import org.harctoolbox.harchardware.comm.LocalSerialPort;
import org.harctoolbox.harchardware.comm.LocalSerialPortBuffered;
import org.harctoolbox.ircore.IrSequence;
import org.harctoolbox.ircore.OddSequenceLengthException;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;

public final class IrFlipperZero extends IrSerial<LocalSerialPortBuffered> implements IReceive{
    public static final String defaultPortName = "/dev/ttyACM0";
    public static final int defaultBaudRate = 115200;
    public static final LocalSerialPort.FlowControl defaultFlowControl = LocalSerialPort.FlowControl.RTSCTS;
    public static final int defaultTimeout = DEFAULT_BEGIN_TIMEOUT;
    private static final int dataSize = 8;
    private static final LocalSerialPort.StopBits stopBits = LocalSerialPort.StopBits.ONE;
    private static final LocalSerialPort.Parity parity = LocalSerialPort.Parity.NONE;
    private int captureMaxSize = DEFAULT_CAPTURE_MAXSIZE;
    private int SerialTimeout = 1000;
    
    
    public IrFlipperZero() throws IOException {
        this(defaultPortName);
    }
    
    public IrFlipperZero(String portName) throws IOException {
        this(portName, false);
    }
    
    public IrFlipperZero(String portName, boolean verbose) throws IOException {
        this(portName, verbose, null);
    }
    
    public IrFlipperZero(boolean verbose, Integer timeout) throws IOException {
        this(defaultPortName, verbose, timeout, defaultBaudRate);
    }
    
    public IrFlipperZero(String portName, boolean verbose, Integer timeout) throws IOException {
        this(portName, verbose, timeout, defaultBaudRate);
    }
    
    public IrFlipperZero(String portName, boolean verbose, Integer timeout, Integer baudRate) throws IOException {
        this(portName, verbose, timeout, baudRate, DEFAULT_CAPTURE_MAXSIZE, defaultFlowControl);
    }
    
    public IrFlipperZero(String portName, boolean verbose, Integer timeout, Integer baudRate, Integer maxLearnLength, LocalSerialPort.FlowControl flowControl)
        throws IOException {
        super(LocalSerialPortBuffered.class, LocalSerialPort.canonicalizePortName(portName, defaultPortName), verbose, timeout != null ? timeout : defaultTimeout, baudRate, dataSize, stopBits, parity, flowControl);
        //serialPort.setVerbose(true); //causes exception - no idea why lmao
    }
    
    
    
    
    
    //IReceive
    @Override
    public void setDebug(int debug) {
        throw new UnsupportedOperationException("Not supported and/or implemented yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public void setBeginTimeout(int integer) throws IOException {
        super.setTimeout(integer);
    }
    
    @Override
    public void setCaptureMaxSize(int captureMaxSize) {
        this.captureMaxSize = captureMaxSize;
    }
    
    @Override
    public void setEndingTimeout(int integer) throws IOException {
    
    }
    
    @Override
    public IrSequence receive() throws HarcHardwareException, IOException, OddSequenceLengthException {
        System.out.println("[TESTING] Started receive method");
        if (!serialPort.isValid()) throw new IOException("Serial connection instance is invalid. Reconnect the device!");
        //serialPort.open(); //apparently it is already opened - if we open it again, it will cause exception.
        if (serialPort.ready()) System.out.println("[TESTING] serial port opened and ready!");
        
        serialPort.setVerbose(false); //ToDo: remove testing
        setSerialTimeout(1000); // timeout for setting-up. The 1000 should be plenty, might decrease that if stable...
        
        getToReceiveReadyState();
        clearIncomingDataBuffer();
    

        IrSequence toReturn = parseAndDecide();
        
        getToIdleState();
        return toReturn;
    }
    
    @Override
    public boolean stopReceive() {
        return false;
    }
    
    //HarcHardware
    
    
    //Helper methods
    
    private void clearIncomingDataBuffer() throws IOException { //flush incoming data buffer
        serialPort.setTimeout(100);
        serialPort.flushInput();    //does not seem to work as expected but eh, why not leave it here...
        while (serialPort.readString(true) != null);
        serialPort.setTimeout(this.SerialTimeout);
    }
    
    private void setSerialTimeout(int timeout) throws IOException {
        this.SerialTimeout = timeout;
        serialPort.setTimeout(this.SerialTimeout);
    }
    
    private void sendInterrupts(int howMany) throws IOException {
        assert howMany > 0;
        for (int i = 0; i < howMany; i++) {
            serialPort.sendByte((byte) 3);  //3 is for Control-C interrupt signal
        }
    }
    
    private boolean getToIdleState() throws IOException {
        //get flipper console to an idle state (next line after CR/LF/Interrupt is ">: " )
        clearIncomingDataBuffer();
        sendInterrupts(3);
        if (Objects.equals(serialPort.readString(true), "") // empty string ""
            && Objects.equals(serialPort.readString(true), ">: ")) //followed by ">: " flipper prompt string
            {
                clearIncomingDataBuffer();
                System.out.println("[TESTING] Flipper in idle state success!");
                return true;
            }
        clearIncomingDataBuffer();
        System.out.println("[TESTING] Flipper in idle state FAILED!");
        return false;
    }
    
    private void getToReceiveReadyState() throws IOException, HarcHardwareException {
        sendInterrupts(2);  //Send itr 2 times, as it may be necessary in come circumstances to fully exit.
        serialPort.sendBytes("ir rx\r".getBytes());
        System.out.println("[TESTING] sent ir rx cmd");
        String buff = serialPort.readString(true);
        while (buff != null) {
            switch (buff) {
                case "Other application is running, close it first":
                    throw new HarcHardwareException("Flipper device is busy! Exit to home screen or reboot it!");
                case "Receiving INFRARED...":
                    this.clearIncomingDataBuffer();
                    System.out.println("[TESTING] Device is in a FULLY READY state!");
                    return;
            }
            buff = serialPort.readString(true);
        }
    }
    
    private IrSequence parseAndDecide() throws IOException, OddSequenceLengthException {
        setTimeout(defaultTimeout);
        String buff = serialPort.readString(true);
        while (buff != null) {
            if (buff.contains("RAW, ") && buff.contains(" samples")) {
                String rawSequence = serialPort.readString(true);
                assert rawSequence != null;
                String[] rawSeqArr = rawSequence.split("\\s* \\s*");
                if (rawSeqArr.length % 2 == 1) rawSequence = rawSequence.concat("0");
                return new IrSequence(rawSequence);
            } else if (buff.matches(Pattern.compile("(\\w)+,?\\s(A:0x..)+,?\\s(C:0x..)+\\s?R?", Pattern.CASE_INSENSITIVE).toString())) {
                System.out.println("[TESTING] An already decoded sequence has been detected:");
                System.out.println("[TESTING] " + buff);
            }
            buff = serialPort.readString(true);
        }
        
        return null;
    }
    
}
