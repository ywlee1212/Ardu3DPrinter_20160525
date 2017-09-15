package com.irnu.ardu3dprinter;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

import com.irnu.bt.BTService;
import com.irnu.bt.DeviceListActivity;
import com.irnu.data.Constant;
import com.irnu.util.SaveImage;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class Ardu3DPrinterActivity extends Activity implements OnClickListener, OnSeekBarChangeListener {

    private static final String TAG = "Ardu3DPrinterActivity";
    private static final boolean D = true;

    private SDListAdapter mSDListAdapter;

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int FILE_SELECT_CODE = 3;

    private String mConnectedDeviceName = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BTService mBTService = null;

    private Context mContext;
    private String mCmdMessage;

    // Main layout
    private Button mBtnConnect;
    private LinearLayout mLayoutControl;
    private LinearLayout mLayoutPrint;
    private LinearLayout mLayoutDownload;
    private Button mBtnLayoutControl;
    private Button mBtnLayoutPrint;
    private Button mBtnLayoutDownload;
    
    private static final int LAYOUT_CONTROL = 100;
    private static final int LAYOUT_PRINT = 101;
    private static final int LAYOUT_DOWNLOAD = 102;
    
    // Control layout
    private TextView mTvXValue;
    private TextView mTvYValue;
    private TextView mTvZValue;
    private Button mBtnXAxis;
    private Button mBtnYAxis;
    private Button mBtnZAxis;
    private Button mBtnEAxis;
    private Button mBtnAllHome;
    private Button mBtnMinus10;
    private Button mBtnMinus01;
    private Button mBtnHome;
    private Button mBtnPlus01;
    private Button mBtnPlus10;
    private TextView mTvHumidity;
    private TextView mTvFanSpeed;
    private SeekBar mSbFanSpeed;
    private ToggleButton mTgBtnFan;
    private TextView mTvExtruderTemp;
    private TextView mTvSetExtruderTemp;
    private SeekBar mSbExtruderTemp;
    private ToggleButton mTgBtnExtruder;

    
    private boolean mBoolInitX = false;
    private boolean mBoolInitY = false;
    private boolean mBoolInitZ = false;

    private static final int X_AXIS = 200;
    private static final int Y_AXIS = 201;
    private static final int Z_AXIS = 202;
    private static final int E_AXIS = 203;
    
    private int mCurAxis = X_AXIS;
    
    private int mCurXValue = 0;
    private int mCurYValue = 0;
    private int mCurZValue = 0;

    // Print layout
    private Button mBtnSendFile;
    private Button mBtnSDRefresh;
    private ListView mSDListView;
    private OnItemClickListener mItemClickListener;
    private Button mBtnCapture;
    private ImageView mIvCapture;

    private static final int BT_SEND_DATA_SIZE = 256;
    private byte[] mBuffer;
    private int mBufSize = 0;
    private ProgressDialog mSendDialog;

    // Download layout
    private Button mBtnReload;
    private WebView mWbDownload;
    
    private static String DOWNLOAD_URL = "http://www.irnumall.co.kr";


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ardu3dprinter);

		mContext = this;

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
	}

   @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // 블루투스가 켜져있지않으면 On 요청
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // 블루투스가 켜져있으면 세션 셋업
        } else {
            if (mBTService == null) initUI();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // 블루투스가 켜져있지 않아서 onStart()에서 Enable 화면 갔다오는 경우 서비스 상태 확인 
        if (mBTService != null) {
            if (mBTService.getState() == BTService.STATE_NONE) {
              mBTService.start();
            }
        }
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 블루투스 서비스 stop
        if (mBTService != null) mBTService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
	        case REQUEST_CONNECT_DEVICE:
	            if (resultCode == Activity.RESULT_OK) {
	                connectDevice(data);
	            }
	            break;
	        case REQUEST_ENABLE_BT:
	            if (resultCode == Activity.RESULT_OK) {
	                initUI();
	            } else {
	                Log.d(TAG, "BT not enabled");
	                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
	                finish();
	            }
	            break;
	        case FILE_SELECT_CODE:
	        	if (resultCode == Activity.RESULT_OK) {
	        		Uri uri = data.getData();
	        		Log.d(TAG, "File Uri: " + uri.toString());
					String path = getPath(this, uri);
	        		Log.d(TAG, "File Path: " + path);
	        		
	        		int cut = path.lastIndexOf('/');
	        		String filename = path.substring(cut + 1);
	        		Log.d(TAG, "filename=" + filename);

					mCmdMessage = "@GCO,/" + checkFilename(filename) + "#";

					sendMessage(mCmdMessage);

	        		File file = new File(path);
	        		if(file != null && file.exists()) {
	        			try {
	        				FileInputStream fis = new FileInputStream(file);
	        				mBufSize = (int)file.length();
	        				Log.d(TAG, "mBufSize="+mBufSize);
	        				mBuffer = new byte[mBufSize];
	        				fis.read(mBuffer);
	        				fis.close();
	        				sendMessage("@SIZ,");
	        				sendMessage(String.valueOf(mBufSize));
	        				sendMessage("#");
	        			} catch (Exception e) {
	        				e.printStackTrace();
	        			}
	        		}
	        		mSendDialog = new ProgressDialog(this);
	        		mSendDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	        		mSendDialog.setMessage(getResources().getString(R.string.txt_send_file));
	        		mSendDialog.setCanceledOnTouchOutside(false);
	        		mSendDialog.show();
	        	}
	        	break;
        }
    }

	@Override
	public void onClick(View v) {
		Intent intent;

		switch(v.getId()) {
			case R.id.btn_bt:
				intent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(intent,REQUEST_CONNECT_DEVICE);
				break;
			case R.id.btn_layout_control:
				viewLayout(LAYOUT_CONTROL);
				break;
			case R.id.btn_layout_print:
				viewLayout(LAYOUT_PRINT);
				break;
			case R.id.btn_layout_download:
				viewLayout(LAYOUT_DOWNLOAD);
				break;
			case R.id.btn_axis_x:
				viewAxis(X_AXIS);
				break;
			case R.id.btn_axis_y:
				viewAxis(Y_AXIS);
				break;
			case R.id.btn_axis_z:
				viewAxis(Z_AXIS);
				break;
			case R.id.btn_axis_e:
				viewAxis(E_AXIS);
				break;
			case R.id.btn_all_home:
				mCurXValue = mCurYValue = mCurZValue = 0;
				mBoolInitX = mBoolInitY = mBoolInitZ = true;
				mCmdMessage = "@CMD,G28#";
				sendMessage(mCmdMessage);
				updateAxisValue();
				break;
			case R.id.btn_minus_10:
				moveAxis(-10);
				break;
			case R.id.btn_minus_01:
				moveAxis(-1);
				break;
			case R.id.btn_home:
				switch(mCurAxis) {
					case X_AXIS:
						mCurXValue = 0;
						mBoolInitX = true;
						mCmdMessage = "@CMD,G28 X0#";
						break;
					case Y_AXIS:
						mCurYValue = 0;
						mBoolInitY = true;
						mCmdMessage = "@CMD,G28 Y0#";
						break;
					case Z_AXIS:
						mCurZValue = 0;
						mBoolInitZ = true;
						mCmdMessage = "@CMD,G28 Z0#";
						break;
				}
				sendMessage(mCmdMessage);
				updateAxisValue();
				break;
			case R.id.btn_plus_01:
				moveAxis(1);
				break;
			case R.id.btn_plus_10:
				moveAxis(10);
				break;
			case R.id.tg_btn_fan:
				if(mTgBtnFan.isChecked()) {
					mCmdMessage = "@CMD,M106 S" + mSbFanSpeed.getProgress() + "#";
				}else {
					mCmdMessage = "@CMD,M107#";
				}
				sendMessage(mCmdMessage);
				break;
			case R.id.tg_btn_extruder:
				if(mTgBtnExtruder.isChecked()) {
					mCmdMessage = "@CMD,M104 T0 S" + mSbExtruderTemp.getProgress() + "#";
				}else {
					mCmdMessage = "@CMD,M104 T0 S0#";
				}
				sendMessage(mCmdMessage);
				break;
			case R.id.btn_send_file:
				intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.setType("*/*");
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				
				startActivityForResult(Intent.createChooser(intent, "Select a File to Upload"), FILE_SELECT_CODE);
				break;
			case R.id.btn_sd_refresh:
				mCmdMessage = "@CMD,M20#";
				sendMessage(mCmdMessage);
				break;
			case R.id.btn_capture:
				mCmdMessage = "@SHT#";
				sendMessage(mCmdMessage);
				break;
			case R.id.btn_reload:
				//mWbDownload.reload();
				AlertDialog.Builder alertText = new AlertDialog.Builder(this);
				alertText.setTitle(R.string.txt_set_url);
				alertText.setMessage(R.string.txt_input_url);
				final EditText title = new EditText(this);
				title.setText("http://192.168.49.10:8080");
				alertText.setView(title);
				alertText.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String name = title.getText().toString();
						mWbDownload.loadUrl(name);
					}
				});

				alertText.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {

							}
						});
				alertText.show();
				break;
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		switch(seekBar.getId()) {
			case R.id.sb_fan_speed:
				mTvFanSpeed.setText(String.valueOf(progress) + " %");
				
				break;
			case R.id.sb_extruder_temp:
				mTvSetExtruderTemp.setText(String.valueOf(progress) + " \u2103");
				break;
		}
		
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	private void moveAxis(int value) {
		switch(mCurAxis) {
			case X_AXIS:
				mCurXValue += value;
				if(mCurXValue < 0) {
					mCurXValue = 0;
				}
				if(mCurXValue > 90) {
					mCurXValue = 90;
				}
				mCmdMessage = "@CMD,G1 X" + mCurXValue +" F4800#";
				break;
			case Y_AXIS:
				mCurYValue += value;
				if(mCurYValue < 0) {
					mCurYValue = 0;
				}
				if(mCurYValue > 90) {
					mCurYValue = 90;
				}
				mCmdMessage = "@CMD,G1 Y" + mCurYValue +" F4800#";
				break;
			case Z_AXIS:
				mCurZValue += value;
				if(mCurZValue < 0) {
					mCurZValue = 0;
				}
				if(mCurZValue > 90) {
					mCurZValue = 90;
				}
				mCmdMessage = "@CMD,G1 Z" + mCurZValue +" F100#";
				break;
			case E_AXIS:
				mCmdMessage = "@CMD,G91#";
				sendMessage(mCmdMessage);
				if(value < 0) {
					mCmdMessage = "@CMD,G1 E" + value +" F1200#";
				}else {
					mCmdMessage = "@CMD,G1 E" + value +" F120#";
				}
				break;
		}
		sendMessage(mCmdMessage);
		if(mCurAxis == E_AXIS) {
			mCmdMessage = "@CMD,G90#";
			sendMessage(mCmdMessage);
		}
		updateAxisValue();
	}
	
	private void updateAxisValue() {
		if(mBoolInitX) {
			mTvXValue.setTextColor(Color.BLACK);
		}else {
			mTvXValue.setTextColor(Color.RED);
		}
		if(mBoolInitY) {
			mTvYValue.setTextColor(Color.BLACK);
		}else {
			mTvYValue.setTextColor(Color.RED);
		}
		if(mBoolInitZ) {
			mTvZValue.setTextColor(Color.BLACK);
		}else {
			mTvZValue.setTextColor(Color.RED);
		}
		mTvXValue.setText(String.valueOf(mCurXValue));
		mTvYValue.setText(String.valueOf(mCurYValue));
		mTvZValue.setText(String.valueOf(mCurZValue));
	}

	private void initUI() {
        Log.d(TAG, "initUI()");

        initMainLayout();
        initControlLayout();
        initPrintLayout();
        initDownloadLayout();

        mBTService = new BTService(this, mHandler);

		Intent intent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
	}

	private void viewLayout(int layout) {
        mLayoutControl.setVisibility(View.GONE);
        mLayoutPrint.setVisibility(View.GONE);
        mLayoutDownload.setVisibility(View.GONE);
        
        mBtnLayoutControl.setSelected(false);
        mBtnLayoutPrint.setSelected(false);
        mBtnLayoutDownload.setSelected(false);
        
        switch(layout) {
        	case LAYOUT_CONTROL:
        		mLayoutControl.setVisibility(View.VISIBLE);
        		mBtnLayoutControl.setSelected(true);
        		break;
        	case LAYOUT_PRINT:
        		mLayoutPrint.setVisibility(View.VISIBLE);
        		mBtnLayoutPrint.setSelected(true);
        		break;
        	case LAYOUT_DOWNLOAD:
        		mLayoutDownload.setVisibility(View.VISIBLE);
        		mBtnLayoutDownload.setSelected(true);
        		break;
        }
	}
	
	private void viewAxis(int axis) {
		mCurAxis = axis;

		mBtnXAxis.setSelected(false);
		mBtnYAxis.setSelected(false);
		mBtnZAxis.setSelected(false);
		mBtnEAxis.setSelected(false);
		mBtnHome.setVisibility(View.VISIBLE);

		switch(axis) {
			case X_AXIS:
				mBtnXAxis.setSelected(true);
				break;
			case Y_AXIS:
				mBtnYAxis.setSelected(true);
				break;
			case Z_AXIS:
				mBtnZAxis.setSelected(true);
				break;
			case E_AXIS:
				mBtnEAxis.setSelected(true);
				mBtnHome.setVisibility(View.INVISIBLE);
				break;
		}
	}

	private void initMainLayout() {
        mBtnConnect = (Button)findViewById(R.id.btn_bt);
        mBtnConnect.setOnClickListener(this);
        mBtnConnect.setBackgroundResource(R.drawable.selector_btn_bt);
        mBtnConnect.setSelected(false);
        
        mLayoutControl = (LinearLayout)findViewById(R.id.layout_control);
        mLayoutPrint = (LinearLayout)findViewById(R.id.layout_print);
        mLayoutDownload = (LinearLayout)findViewById(R.id.layout_download);
        
        mBtnLayoutControl = (Button)findViewById(R.id.btn_layout_control);
        mBtnLayoutControl.setOnClickListener(this);
        mBtnLayoutPrint = (Button)findViewById(R.id.btn_layout_print);
        mBtnLayoutPrint.setOnClickListener(this);
        mBtnLayoutDownload = (Button)findViewById(R.id.btn_layout_download);
        mBtnLayoutDownload.setOnClickListener(this);
        
        viewLayout(LAYOUT_CONTROL);
	}

	private void initControlLayout() {
		mTvXValue = (TextView)findViewById(R.id.tv_x_axis_value);
		mTvYValue = (TextView)findViewById(R.id.tv_y_axis_value);
		mTvZValue = (TextView)findViewById(R.id.tv_z_axis_value);
		
		mBtnXAxis = (Button)findViewById(R.id.btn_axis_x);
		mBtnXAxis.setOnClickListener(this);
		mBtnYAxis = (Button)findViewById(R.id.btn_axis_y);
		mBtnYAxis.setOnClickListener(this);
		mBtnZAxis = (Button)findViewById(R.id.btn_axis_z);
		mBtnZAxis.setOnClickListener(this);
		mBtnEAxis = (Button)findViewById(R.id.btn_axis_e);
		mBtnEAxis.setOnClickListener(this);
		mBtnAllHome = (Button)findViewById(R.id.btn_all_home);
		mBtnAllHome.setOnClickListener(this);
		mBtnMinus10 = (Button)findViewById(R.id.btn_minus_10);
		mBtnMinus10.setOnClickListener(this);
		mBtnMinus01 = (Button)findViewById(R.id.btn_minus_01);
		mBtnMinus01.setOnClickListener(this);
		mBtnHome = (Button)findViewById(R.id.btn_home);
		mBtnHome.setOnClickListener(this);
		mBtnPlus01 = (Button)findViewById(R.id.btn_plus_01);
		mBtnPlus01.setOnClickListener(this);
		mBtnPlus10 = (Button)findViewById(R.id.btn_plus_10);
		mBtnPlus10.setOnClickListener(this);

		mTvHumidity = (TextView)findViewById(R.id.tv_humidity);

		mTvFanSpeed = (TextView)findViewById(R.id.tv_fan_speed);
		mTvFanSpeed.setText("50" + " %");
		mSbFanSpeed = (SeekBar)findViewById(R.id.sb_fan_speed);
		mSbFanSpeed.setOnSeekBarChangeListener(this);
		mSbFanSpeed.setProgress(50);
		mTgBtnFan = (ToggleButton)findViewById(R.id.tg_btn_fan);
		mTgBtnFan.setOnClickListener(this);

		mTvExtruderTemp = (TextView)findViewById(R.id.tv_extruder_temp);
		mTvSetExtruderTemp = (TextView)findViewById(R.id.tv_set_extruder_temp);
		mTvSetExtruderTemp.setText("200" + " \u2103");
		mSbExtruderTemp = (SeekBar)findViewById(R.id.sb_extruder_temp);
		mSbExtruderTemp.setOnSeekBarChangeListener(this);
		mSbExtruderTemp.setProgress(200);
		mTgBtnExtruder = (ToggleButton)findViewById(R.id.tg_btn_extruder);
		mTgBtnExtruder.setOnClickListener(this);

		updateAxisValue();
		viewAxis(X_AXIS);
	}

	private void initPrintLayout() {
		mBtnSendFile = (Button)findViewById(R.id.btn_send_file);
		mBtnSendFile.setOnClickListener(this);
		
		mBtnSDRefresh = (Button)findViewById(R.id.btn_sd_refresh);
		mBtnSDRefresh.setOnClickListener(this);
		
		mBtnCapture = (Button)findViewById(R.id.btn_capture);
		mBtnCapture.setOnClickListener(this);

		mIvCapture = (ImageView)findViewById(R.id.img_capture);

        mItemClickListener = new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parentView, View clickedView, int position, long id) {
				String filename = mSDListAdapter.getFilename(position);
				if(filename == null) return;
				filename = filename.toLowerCase();
				mCmdMessage = "@CMD,M23 " + filename + "#";
				sendMessage(mCmdMessage);
				mCmdMessage = "@CMD,M24#";
				sendMessage(mCmdMessage);
			}
		};
        mSDListView = (ListView)findViewById(R.id.lv_sd_file);
        mSDListAdapter = new SDListAdapter();
        mSDListView.setAdapter(mSDListAdapter);
        mSDListView.setOnItemClickListener(mItemClickListener);
        mSDListView.setEmptyView(findViewById(R.id.tv_empty));
	}

	private void initDownloadLayout() {
		mBtnReload = (Button)findViewById(R.id.btn_reload);
		mBtnReload.setOnClickListener(this);

		mWbDownload = (WebView)findViewById(R.id.wb_download);
		mWbDownload.getSettings().setJavaScriptEnabled(true);
		mWbDownload.clearHistory();
		mWbDownload.clearFormData();
		mWbDownload.clearCache(true);
		//mWbDownload.loadUrl(DOWNLOAD_URL);
	}

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mBTService.getState() != BTService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BTService to write
            byte[] send = message.getBytes();
            mBTService.write(send);
        }
    }

    private void sendMessage(byte[] message) {
        if (mBTService.getState() != BTService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        mBTService.write(message);
    }

    private void connectDevice(Intent data) {
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BLuetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mBTService.connect(device);
    }

    private void setResultValue(String readMessage, byte[] seqnum) {
    	String cmd = readMessage.substring(0, 3);
    	String data = readMessage.substring(4);

    	if("FLS".equals(cmd)) {
    		mSDListAdapter.clear();
	    	String[] filenames = data.split("\n");

	    	for(int i=1; i < filenames.length; i++) {	// skip EMPTY\n
	    		mSDListAdapter.addFilename(filenames[i]);
	    	}
	    	mSDListAdapter.notifyDataSetChanged();
    	}else if("SEQ".equals(cmd)) {
    		int seqNum = byteArrayToInt(seqnum);
    		Log.d(TAG,"++seqNum="+seqNum);
    		int count = getReadCount(seqNum);
    		sendMessage(intToByteArray(seqNum));
    		mBTService.writeFile(mBuffer, seqNum*BT_SEND_DATA_SIZE, count);
    		byte[] checkSum = new byte[1];
    		checkSum[0] = 0x00;
    		for(int i=0; i<count; i++) {
    			checkSum[0] ^= mBuffer[seqNum*BT_SEND_DATA_SIZE+i];
    		}
    		sendMessage(checkSum);
    		mSendDialog.setProgress((seqNum*BT_SEND_DATA_SIZE+count)*100/mBufSize);
    		if((seqNum*BT_SEND_DATA_SIZE + count) >= mBufSize) {
    			mSendDialog.dismiss();
    		}
    	}else if("TMP".equals(cmd)) {
    		mTvExtruderTemp.setText(data + " \u2103");
    	}else if("HUM".equals(cmd)) {
    		mTvHumidity.setText(data + " %");
    	}
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
	public byte[] intToByteArray(int value) {
		byte[] byteArray = new byte[3];
		byteArray[0] = (byte)(value);
		byteArray[1] = (byte)(value >> 8);
		byteArray[2] = (byte)(value >> 16);
		return byteArray;
	}
	
	public int byteArrayToInt(byte bytes[]) {
		return (int)(((bytes[2] & 0xff) << 16) |
				((bytes[1] & 0xff) << 8) |
				(bytes[0] & 0xff));
	}

	public int getReadCount(int seqNum) {
		int count = 0;
		int remainder = mBufSize % BT_SEND_DATA_SIZE;
		if(remainder > 0) {
			if(seqNum == (mBufSize/BT_SEND_DATA_SIZE)) {
				count = remainder;
			}else {
				count = BT_SEND_DATA_SIZE;
			}
		}else {
			count = BT_SEND_DATA_SIZE;
		}
		return count;
	}

	public String checkFilename(String filename) {
		String result="";
		
		int index = filename.lastIndexOf('.');
		String name = filename.substring(0, index);
		String extName = filename.substring(index+1);
		
		if(name.length() > 8) {
			name = name.substring(0, 6) + "~1";
		}
		
		if(extName.length() > 3) {
			extName = extName.substring(0, 3);
		}
		result = name + "." + extName;
		return result;
	}

    private final Handler mHandler = new Handler() {

    	private ProgressDialog mDialog;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
	            case Constant.MESSAGE_STATE_CHANGE:
	                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
	                if(mDialog != null && mDialog.isShowing()) {
	                	mDialog.dismiss();
	                }
	                switch (msg.arg1) {
	                case BTService.STATE_CONNECTED:
	                	mBtnConnect.setSelected(true);
	                    break;
	                case BTService.STATE_CONNECTING:
	                	mBtnConnect.setSelected(false);
	                    break;
	                case BTService.STATE_LISTEN:
	                case BTService.STATE_NONE:
	                	mBtnConnect.setSelected(false);
	                    break;
	                }
	                break;
	            case Constant.MESSAGE_WRITE:
	                break;
	            case Constant.MESSAGE_READ:
	                byte[] readBuf = (byte[]) msg.obj;

	                if(msg.arg2 == 1) {	// Image
	                	int offsetHead = 4;
	        			BitmapFactory.Options options = new BitmapFactory.Options();
	        			options.inSampleSize = 1;
	        			Bitmap bm = BitmapFactory.decodeByteArray(readBuf, offsetHead, msg.arg1 - offsetHead, options);
	        			SaveImage.SaveBitmapToFileCache(mContext, bm);
	        			mIvCapture.setImageBitmap(bm);
	        			mDialog.dismiss();
	                } else {	// Other
		                String readMessage = new String(readBuf, 0, msg.arg1);
		                Log.e(TAG,"readMessage :"+readMessage);
		                byte[] seqnum = new byte[3];
		                seqnum[0] = readBuf[4];
		                seqnum[1] = readBuf[5];
		                seqnum[2] = readBuf[6];
		                setResultValue(readMessage, seqnum);
	                }
	                break;
	            case Constant.MESSAGE_DEVICE_NAME:
	                // save the connected device's name
	                mConnectedDeviceName = msg.getData().getString(Constant.DEVICE_NAME);
	                Toast.makeText(getApplicationContext(), "Connected to "
	                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
	                break;
	            case Constant.MESSAGE_TOAST:
	                Toast.makeText(getApplicationContext(), msg.getData().getString(Constant.TOAST),
	                               Toast.LENGTH_SHORT).show();
	                break;
	            case Constant.MESSAGE_START_IMAGE:
					mDialog = new ProgressDialog(Ardu3DPrinterActivity.this);
					mDialog.setMessage(getResources().getString(R.string.txt_load_image));
					//mDialog.setCancelable(false);
					mDialog.setCanceledOnTouchOutside(false);
					mDialog.show();
	            	break;
            }
        }
    };

    @SuppressLint("NewApi")
	public static String getPath(final Context context, final Uri uri) 
    {
	    final boolean isKitKatOrAbove = Build.VERSION.SDK_INT >=  Build.VERSION_CODES.KITKAT;
	
	    // DocumentProvider
	    if (isKitKatOrAbove && DocumentsContract.isDocumentUri(context, uri)) {
	        // ExternalStorageProvider
	        if (isExternalStorageDocument(uri)) {
	            final String docId = DocumentsContract.getDocumentId(uri);
	            final String[] split = docId.split(":");
	            final String type = split[0];
	
	            if ("primary".equalsIgnoreCase(type)) {
	                return Environment.getExternalStorageDirectory() + "/" + split[1];
	            }
	
	            // TODO handle non-primary volumes
	        }
	        // DownloadsProvider
	        else if (isDownloadsDocument(uri)) {
	
	            final String id = DocumentsContract.getDocumentId(uri);
	            final Uri contentUri = ContentUris.withAppendedId(
	                    Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
	
	            return getDataColumn(context, contentUri, null, null);
	        }
	        // MediaProvider
	        else if (isMediaDocument(uri)) {
	            final String docId = DocumentsContract.getDocumentId(uri);
	            final String[] split = docId.split(":");
	            final String type = split[0];
	
	            Uri contentUri = null;
	            if ("image".equals(type)) {
	                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
	            } else if ("video".equals(type)) {
	                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
	            } else if ("audio".equals(type)) {
	                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
	            }
	
	            final String selection = "_id=?";
	            final String[] selectionArgs = new String[] {
	                    split[1]
	            };
	
	            return getDataColumn(context, contentUri, selection, selectionArgs);
	        }
	    }
	    // MediaStore (and general)
	    else if ("content".equalsIgnoreCase(uri.getScheme())) {
	        return getDataColumn(context, uri, null, null);
	    }
	    // File
	    else if ("file".equalsIgnoreCase(uri.getScheme())) {
	        return uri.getPath();
	    }
	
	    return null;
	}
	
	/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @param selection (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	public static String getDataColumn(Context context, Uri uri, String selection,
	                                   String[] selectionArgs) {
	
	    Cursor cursor = null;
	    final String column = "_data";
	    final String[] projection = {
	            column
	    };
	
	    try {
	        cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
	                null);
	        if (cursor != null && cursor.moveToFirst()) {
	            final int column_index = cursor.getColumnIndexOrThrow(column);
	            return cursor.getString(column_index);
	        }
	    } finally {
	        if (cursor != null)
	            cursor.close();
	    }
	    return null;
	}
	
	
	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
	    return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}
	
	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	public static boolean isDownloadsDocument(Uri uri) {
	    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}
	
	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	public static boolean isMediaDocument(Uri uri) {
	    return "com.android.providers.media.documents".equals(uri.getAuthority());
	}
	
	private class SDListAdapter extends BaseAdapter {
		private ArrayList<String> mSDList;
		private LayoutInflater mInflator;

		public SDListAdapter() {
			super();
			mSDList = new ArrayList<String>();
			mInflator = Ardu3DPrinterActivity.this.getLayoutInflater();
		}

		public void addFilename(String filename) {
			if(!mSDList.contains(filename)) {
				mSDList.add(filename);
			}
		}

		public String getFilename(int position) {
			return mSDList.get(position);
		}

		public void clear() {
			mSDList.clear();
		}

		@Override
		public int getCount() {
			return mSDList.size();
		}

		@Override
		public Object getItem(int i) {
			return mSDList.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			ViewHolder viewHolder;
			
			if (view == null) {
				view = mInflator.inflate(R.layout.sd_file_list, null);
				viewHolder = new ViewHolder();
				viewHolder.filename = (TextView) view.findViewById(R.id.tv_sd_filename);
				view.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) view.getTag();
			}
			
			String fileName = mSDList.get(i);
			if(fileName != null && fileName.length() > 0) {
				viewHolder.filename.setText(fileName);
			}
			return view;
		}
		
		
	}
	
    static class ViewHolder {
        TextView filename;
    }

}
