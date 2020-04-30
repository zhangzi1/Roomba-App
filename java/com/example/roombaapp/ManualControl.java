package com.example.roombaapp;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class ManualControl extends AppCompatActivity {
    private TCP sender;
    private TCP checker;
    private ImageView map;
    private TextView status_text;
    private TextView battery_text;
    private boolean stop = false;
    private String ip;
    private String port;
    private boolean dialog_enable = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.control_manual);

        //Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //DrawerLayout
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        //NavigationView
        NavigationView navView = findViewById(R.id.nav_view);
        View headerLayout = navView.inflateHeaderView(R.layout.nav_header);
        TextView nav_username = headerLayout.findViewById(R.id.username);
        TextView nav_email = headerLayout.findViewById(R.id.mail);
        ImageView nav_avatar = headerLayout.findViewById(R.id.icon_image);
        navView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                Intent intent = null;
                boolean mode = ((MyApplication) getApplication()).getMF();
                if (!mode && (item.getItemId() == R.id.nav_1 || item.getItemId() == R.id.nav_4)) {
                    // AlertDialog
                    AlertDialog.Builder dialog = new AlertDialog.Builder(ManualControl.this);
                    dialog.setTitle("It's in Automatic Mode!");
                    dialog.setMessage("Please switch mode first.");
                    dialog.setCancelable(true);
                    dialog.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // do nothing
                        }
                    });
                    dialog.show();

                } else if (mode && item.getItemId() == R.id.nav_6) {
                    // AlertDialog
                    AlertDialog.Builder dialog = new AlertDialog.Builder(ManualControl.this);
                    dialog.setTitle("It's in Manual Mode!");
                    dialog.setMessage("Please switch mode first.");
                    dialog.setCancelable(true);
                    dialog.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // do nothing
                        }
                    });
                    dialog.show();

                } else {
                    switch (item.getItemId()) {
                        case R.id.nav_1:
                            intent = new Intent(ManualControl.this, BeepControl.class);
                            ((MyApplication) getApplication()).setSender(sender);
                            ((MyApplication) getApplication()).setChecker(checker);
                            break;
                        case R.id.nav_2:
                            intent = new Intent(ManualControl.this, Setting.class);
                            ((MyApplication) getApplication()).setSender(null);
                            ((MyApplication) getApplication()).setChecker(null);
                            break;
                        case R.id.nav_3:
                            intent = new Intent(ManualControl.this, Login.class);
                            ((MyApplication) getApplication()).setSender(null);
                            ((MyApplication) getApplication()).setChecker(null);
                            break;
                        case R.id.nav_4:
                            intent = new Intent(ManualControl.this, ManualControl.class);
                            ((MyApplication) getApplication()).setSender(sender);
                            ((MyApplication) getApplication()).setChecker(checker);
                            break;
                        case R.id.nav_5:
                            intent = new Intent(ManualControl.this, GeneralPanel.class);
                            ((MyApplication) getApplication()).setSender(sender);
                            ((MyApplication) getApplication()).setChecker(checker);
                            break;
                        case R.id.nav_6:
                            intent = new Intent(ManualControl.this, RandomWalk.class);
                            ((MyApplication) getApplication()).setSender(sender);
                            ((MyApplication) getApplication()).setChecker(checker);
                            break;
                        default:
                    }
                    startActivity(intent);
                    finish();
                }
                DrawerLayout drawer = findViewById(R.id.drawer_layout);
                drawer.closeDrawer(GravityCompat.START);
                return true;
            }
        });

        /* ************************************************************************************** */

        // SharedPreferences
        SharedPreferences pref = getApplicationContext().getSharedPreferences("Setting", 0);
        ip = pref.getString("ip", null);
        port = pref.getString("port", null);
        Log.d("Setting", "IP: " + ip + "  Port: " + port);

        // launch Setting?
        if (ip == null || port == null) {
            ip = "";
            port = "8866";
            Intent intent = new Intent(ManualControl.this, Setting.class);
            startActivity(intent);
            finish();
        }

        // WebView
        WebView webView;
        webView = (WebView) findViewById(R.id.webView1);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        webView.getSettings().setPluginState(WebSettings.PluginState.ON_DEMAND);
        webView.getSettings().setJavaScriptEnabled(true);
        // load the customURL with the URL of the page you want to display
        String pageURL = "http://" + ip + ":8082/";
        webView.loadUrl(pageURL);

        // start connection
        sender = ((MyApplication) getApplication()).getSender();
        checker = ((MyApplication) getApplication()).getChecker();
        if (sender == null) {
            sender = new TCP(ip, Integer.parseInt(port));
            sender.setSocket();
        }
        if (checker == null) {
            checker = new TCP(ip, Integer.parseInt(port));
            checker.setSocket();
        }

        // connectivity-checking thread
        Thread check = new Thread() {
            @Override
            public void run() {
                boolean previous_state = checker.status;
                String previous_battery = checker.buffer;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                checker.receive();
                while (!stop) {
                    // send beacon
                    try {
                        Thread.sleep(1000);
                        checker.send("manu");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // check status
                    if (previous_state != checker.status) {
                        previous_state = !previous_state;
                        Message msg = new Message();
                        msg.obj = previous_state;
                        connection_handler.sendMessage(msg);
                    }
                    // check battery
                    if (!previous_battery.equals(checker.buffer)) {
                        previous_battery = checker.buffer;
                        Message msg = new Message();
                        msg.obj = previous_battery;
                        battery_handler.sendMessage(msg);
                    }
                    // reconnection
                    if (!previous_state && !stop) {
                        if (sender.status)
                            sender.close();
                        if (checker.status)
                            checker.close();
                        sender = new TCP(ip, Integer.parseInt(port));
                        sender.setSocket();
                        checker = new TCP(ip, Integer.parseInt(port));
                        checker.setSocket();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        checker.receive();
                    }
                }
            }
        };
        check.start();

        Thread http = new Thread() {
            @Override
            public void run() {

                //创建OkHttpClient对象
                OkHttpClient client = new OkHttpClient();
                //创建Request
                Request request = new Request.Builder()
                        .url("http://" + ip + ":5000/manu_map")//访问连接
                        .get()
                        .build();
                //创建Call对象
                while (!stop) {
                    try {
                        Thread.sleep(1000);
                        //创建Call对象
                        Call call = client.newCall(request);
                        //通过execute()方法获得请求响应的Response对象
                        Response response = call.execute();
                        if (response.isSuccessful()) {
                            InputStream inputStream = response.body().byteStream();//得到图片的流
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            Message msg = new Message();
                            msg.obj = bitmap;
                            map_handler.sendMessage(msg);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        http.start();

        // ImageView
        map = findViewById(R.id.map);

        // TextView
        status_text = findViewById(R.id.status_text);
        if (checker.status)
            status_text.setText("Connected");
        else
            status_text.setText("Disconnected");
        battery_text = findViewById(R.id.battery_text);
        battery_text.setText(checker.buffer);

        // Buttons
        Button forward = findViewById(R.id.forward);
        Button backward = findViewById(R.id.backward);
        Button left = findViewById(R.id.left);
        Button right = findViewById(R.id.right);

        // EditText
        final EditText speed = findViewById(R.id.speed_text);

        //forward
        forward.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (checker.status) {
                        String speed_percent = speed.getText().toString();
                        sender.send("FWRD" + speed_percent);
                        Toast.makeText(ManualControl.this, "Roomba moving forward", Toast.LENGTH_SHORT).show();
                    } else
                        dialog("Connection failed!", "Please check parameters or server status.");
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (checker.status) {
                        sender.send("STOP");
                    } else
                        dialog("Connection failed!", "Please check parameters or server status.");
                }
                return true;
            }
        });
        //back
        backward.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (checker.status) {
                        String speed_percent = speed.getText().toString();
                        sender.send("BWRD" + speed_percent);
                        Toast.makeText(ManualControl.this, "Roomba moving backward", Toast.LENGTH_SHORT).show();
                    } else
                        dialog("Connection failed!", "Please check parameters or server status.");
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (checker.status) {
                        sender.send("STOP");
                    } else
                        dialog("Connection failed!", "Please check parameters or server status.");
                }
                return true;
            }
        });
        //left
        left.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (checker.status) {
                        String speed_percent = speed.getText().toString();
                        sender.send("LEFT" + speed_percent);
                        Toast.makeText(ManualControl.this, "Roomba turning left", Toast.LENGTH_SHORT).show();
                    } else
                        dialog("Connection failed!", "Please check parameters or server status.");
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (checker.status) {
                        sender.send("STOP");
                    } else
                        dialog("Connection failed!", "Please check parameters or server status.");
                }
                return true;
            }
        });
        //right
        right.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (checker.status) {
                        String speed_percent = speed.getText().toString();
                        sender.send("RGHT" + speed_percent);
                        Toast.makeText(ManualControl.this, "Roomba turning right", Toast.LENGTH_SHORT).show();
                    } else
                        dialog("Connection failed!", "Please check parameters or server status.");
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (checker.status) {
                        sender.send("STOP");
                    } else
                        dialog("Connection failed!", "Please check parameters or server status.");
                }
                return true;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // sender.close();
        // checker.close();
        stop = true;
    }

    private void dialog(String title, String message) {
        if (dialog_enable) {
            dialog_enable = false;
            // AlertDialog
            AlertDialog.Builder dialog = new AlertDialog.Builder(ManualControl.this);
            dialog.setTitle(title);
            dialog.setMessage(message);
            dialog.setCancelable(true);
            dialog.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog_enable = true;
                }
            });
            dialog.show();
        }
    }

    private Handler map_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            map.setImageBitmap((Bitmap) msg.obj);
        }
    };
    private Handler connection_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if ((boolean) msg.obj) {
                status_text.setText("Connected");
            } else {
                status_text.setText("Disconnected");
            }
        }
    };

    private Handler battery_handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            battery_text.setText((String) msg.obj);
        }
    };
}
