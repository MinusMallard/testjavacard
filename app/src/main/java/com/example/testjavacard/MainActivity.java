package com.example.testjavacard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.gotrust.sesdapi.SDSCException;
import com.gotrust.sesdapi.SESDAPI;

import java.io.File;
import java.util.Objects;
import java.util.Vector;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 1;
    private SESDAPI sesdapi;
    private TextView resultTextView;
    private EditText inputEditText;
    private static final byte INS_ECHO = (byte) 0x01; // Echo a message
    private static final byte INS_RANDOM = (byte) 0x02; // Generate random data
    private static final byte[] AID = {
            (byte) 0xA0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x62,
            (byte) 0x03, (byte) 0x01, (byte) 0x08, (byte) 0x01
    };

    //copy from sample app
    private String ioFileName;
    private boolean inTesting = false;

    Thread createIoFileThread;
    CreateIoFileRunnable createIoFileRunnable;
    QueryCreateIoFileProgressTask queryCreateIoFileProgressTask;

    public static final int MSG_CREATE_FILE_FINISHED = 1;

    private Handler createFileHandler;

    //add
    SESDAPI obj = new SESDAPI();

    String ioFilePath = "";

    //The path of secure microSD

    int fd = -1;

    private PowerManager.WakeLock mWakeLock;
    private PowerManager mPowerManager;
    private boolean ifLocked;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ioFileName = "SMART_IO.CRD";

        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE);
        }


        resultTextView = findViewById(R.id.resultTextView);
        inputEditText = findViewById(R.id.inputEditText);
        goCreateIoFile();

        Button echoButton = findViewById(R.id.echoButton);
        echoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendEchoCommand();
            }
        });

        Button randomButton = findViewById(R.id.randomButton);
        randomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendRandomCommand();
            }
        });
    }

    /**
     * Get the I/O file path on the secure microSD.
     * @return Returns the I/O file path; returns null if microSD not found.
     */
    private String getIoFilePath()
    {
        File[] fileArray = getExternalFilesDirs(null);
        if(fileArray.length < 2)
        {
            return null;	// microSD not inserted
        }
        else if(fileArray[1] == null)
        {
            return null;	// microSD may not be mounted
        }

        String tmp = fileArray[1].getPath();
        return tmp.substring(0, tmp.indexOf("/files") + 1) + ioFileName;
    }

    private void goCreateIoFile()
    {

        String tempIoFilePath;

        tempIoFilePath = getIoFilePath();

        if(tempIoFilePath == null)
        {
            Log.d("1", "getIoFilePath returned null");
            return;
        }

        obj.SDSCCreateIoFile(tempIoFilePath);
        goListDev();
    }

    private void goListDev()
    {

        long time = System.currentTimeMillis();

        obj.SDSCSetProductType(SESDAPI.SDSC_PRODUCT_TYPE_NS);

        // list dev
        Vector<String> vDevs = new Vector<String>();
        try
        {
            String tempIoFilePath = getIoFilePath();
            if(tempIoFilePath == null)
            {
                Log.d("2", "Please insert microSD first!");
                return;
            }

            vDevs = obj.SDSCListDevs(tempIoFilePath);
            if(vDevs.isEmpty())
                Log.d("3", "No secure microSD found.");
            else
            {
                StringBuilder strTemp = new StringBuilder();
                for(int i=0;i<vDevs.size();i++)
                    strTemp.append((String) vDevs.elementAt(i)).append("\n");
                Log.d("-1", strTemp.toString());
                // save first device
                ioFilePath = vDevs.elementAt(0);
            }
        }
        catch(SDSCException e)
        {
            Log.d("4", "Error: " + e.getMessage());
        }
        goConnectDev();
    }

    private void goConnectDev()
    {

        // get sdk version
        try
        {
            String strSDKVer;
            strSDKVer = obj.SDSCGetSDKVersion();
            Log.d("5", "SDK Version: " + strSDKVer);
        }
        catch(SDSCException e)
        {
            Log.d("6", Objects.requireNonNull(e.getMessage()));
        }

        // get protocol version
        try
        {
            String strProtocolVer;
            strProtocolVer = obj.SDSCGetProtocolVersion();
            Log.d("7", "Protocol version: " + strProtocolVer);
        }
        catch(SDSCException e)
        {
            Log.d("8", Objects.requireNonNull(e.getMessage()));
        }

        long time = System.currentTimeMillis();

        // connect dev
        try
        {
            fd = obj.SDSCConnectDev(ioFilePath);

            Log.d("9", "SDSCConnectDev ok. Handle = " + fd);
        }
        catch(SDSCException e)
        {
            Log.d("10", Objects.requireNonNull(e.getMessage()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        disconnectFromSecureElement();
    }

    private void connectToSecureElement() {
        try {
            File[] externalStorageVolumes = getExternalFilesDirs(null);
            for (File file : externalStorageVolumes) {
                if (file != null && Environment.isExternalStorageRemovable(file)) {
                    String sdCardPath = file.getAbsolutePath();
                    String ioFilePath = sdCardPath.substring(0, sdCardPath.indexOf("/Android")) + "/SMART_IO.CRD";

                    Log.d(TAG, "Trying to connect using I/O file path: " + ioFilePath);

                    if (ioFilePath != null) {
                        sesdapi.SDSCSetProductType(SESDAPI.SDSC_PRODUCT_TYPE_NS); // Set product type (NS in this case)

                        // List available devices
                        Vector<String> devices = sesdapi.SDSCListDevs(ioFilePath);

                        if (!devices.isEmpty()) {
                            // Connect to the first available device (assuming it's your GO-Trust microSD)
                            fd = sesdapi.SDSCConnectDev(devices.get(0));
                            Log.d(TAG, "Connected to secure element. File descriptor: " + fd);

                            // Select the applet using its AID
                            byte[] selectCommand = buildSelectAPDU(AID);
                            byte[] response = new byte[256];
                            int responseLength = sesdapi.SDSCTransmitEx(fd, selectCommand, 0, selectCommand.length, SESDAPI.SDSC_DEV_DEFAULT_TIME_OUT, response);

                            // Check if applet selection was successful
                            if (responseLength >= 2 && response[responseLength - 2] == (byte) 0x90 && response[responseLength - 1] == (byte) 0x00) {
                                Log.d(TAG, "Applet selected successfully.");
                            } else {
                                Log.e(TAG, "Error selecting applet.");
                            }
                        } else {
                            Log.e(TAG, "No secure element found.");
                        }
                    } else {
                        Log.e(TAG, "Unable to determine I/O file path.");
                    }
                }
            }
            Log.e(TAG, "No secure element found or error connecting.");
        } catch (SDSCException e) {
            Log.e(TAG, "Error connecting to secure element: " + e.getMessage());
        }
    }


    // Helper function to build SELECT APDU command
    private byte[] buildSelectAPDU(byte[] aid) {
        byte[] apdu = new byte[5 + aid.length];
        apdu[0] = 0x00;  // CLA
        apdu[1] = (byte) 0xA4;  // INS (SELECT)
        apdu[2] = 0x04;  // P1 (Select by AID)
        apdu[3] = 0x00;  // P2 (First or only occurrence)
        apdu[4] = (byte) aid.length;  // Lc (Length of AID)
        System.arraycopy(aid, 0, apdu, 5, aid.length);
        return apdu;
    }

    private void disconnectFromSecureElement() {
        if (fd != -1) {
            try {
                sesdapi.SDSCDisconnectDev(fd);
                Log.d(TAG, "Disconnected from secure element.");
            } catch (SDSCException e) {
                Log.e(TAG, "Error disconnecting from secure element: " + e.getMessage());
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void sendEchoCommand() {
        if (fd != -1) {
            try {
                String message = inputEditText.getText().toString();
                byte[] command = buildAPDU(INS_ECHO, message.getBytes());
                byte[] response = new byte[512];
                int responseLength = sesdapi.SDSCTransmitEx(fd, command, 0, command.length, SESDAPI.SDSC_DEV_DEFAULT_TIME_OUT, response);
                String echoedMessage = new String(response, 0, responseLength - 2); // Exclude status bytes
                resultTextView.setText("Echoed message: " + echoedMessage);
            } catch (SDSCException e) {
                Log.e(TAG, "Error sending APDU command: " + e.getMessage());
            }
        }
    }

    private void sendRandomCommand() {
        if (fd != -1) {
            try {
                byte[] command = buildAPDU(INS_RANDOM, null);
                byte[] response = new byte[512];
                int responseLength = sesdapi.SDSCTransmitEx(fd, command, 0, command.length, SESDAPI.SDSC_DEV_DEFAULT_TIME_OUT, response);
                String randomData = bytesToHex(response, 0, responseLength - 2); // Exclude status bytes
                resultTextView.setText("Random data: " + randomData);
            } catch (SDSCException e) {
                Log.e(TAG, "Error sending APDU command: " + e.getMessage());
            }
        }
    }

    // Helper function to build APDU commands
    private byte[] buildAPDU(byte ins, byte[] data) {
        byte[] apdu = new byte[5 + (data != null ? data.length : 0)];
        apdu[0] = 0x00; // CLA
        apdu[1] = ins; // INS
        apdu[2] = 0x00; // P1
        apdu[3] = 0x00; // P2
        if (data != null) {
            apdu[4] = (byte) data.length; // Lc
            System.arraycopy(data, 0, apdu, 5, data.length);
        }
        return apdu;
    }

    // Helper function to convert bytes to hex string
    private String bytesToHex(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < offset + length; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        return sb.toString();
    }

    protected class QueryCreateIoFileProgressTask extends AsyncTask<String, Short, Short> {
        String ioFilePath;

        @Override
        protected Short doInBackground(String... ioFilePaths) {
            short progress = 0;
            this.ioFilePath = ioFilePaths[0];

            while(progress < 100 && !isCancelled())
            {
                try
                {
                    Thread.sleep(1000);
                    progress = obj.SDSCGetCreateIoFileProgress(ioFilePaths[0]);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (SDSCException e) {
                    if(e.getReason() == SESDAPI.SDSC_DEV_NO_FILE_ERROR)
                    {
                        progress = 0;
                    }
                    else
                    {
                        cancel(false);
                    }
                }


                publishProgress(progress);
            }

            return progress;
        }

//        @Override
//        protected void onProgressUpdate(Short... values) {
//            mTextResult.setText("Creating I/O file under: " + ioFilePath + "\n");
//            mTextResult.append("Progress: " + values[0] + "%");
//        }
    }
}

class CreateIoFileRunnable implements Runnable
{
    private SESDAPI obj;
    private String ioFilePath;
    private Handler handler;
    private int result;
    private int extraSpaceNeeded;

    CreateIoFileRunnable(SESDAPI obj, String ioFilePath, Handler handler)
    {
        this.obj = obj;
        this.ioFilePath = ioFilePath;
        this.handler = handler;
    }

    public void run()
    {
        try
        {
            extraSpaceNeeded = obj.SDSCCreateIoFile(ioFilePath);
            if(extraSpaceNeeded > 0)
                result = SESDAPI.SDSC_DEV_NO_SPACE_ERROR;
            else
                result = 0;
        }
        catch(SDSCException e)
        {
            result = e.getReason();
        }

        handler.sendEmptyMessage(100);
    }

    public int getResult()
    {
        return result;
    }

    public int getExtraSpaceNeeded()
    {
        return extraSpaceNeeded;
    }
}

