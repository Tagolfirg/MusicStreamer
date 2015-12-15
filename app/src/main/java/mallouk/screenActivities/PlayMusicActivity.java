package mallouk.screenActivities;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mallouk.musicstreamer.BucketManager;
import mallouk.musicstreamer.R;

public class PlayMusicActivity extends Activity implements View.OnTouchListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnBufferingUpdateListener, AdapterView.OnItemClickListener,
        View.OnClickListener {

    private ListView playMusicView = null;
    private BucketManager bucketManager = null;
    private String playPause = "Play";
    private ImageButton repeatButton = null;
    private ImageButton playPauseButton = null;

    private TextView currentDirectoryView = null;
    private SeekBar seekBarProgress;
    private MediaPlayer player;
    private int numItemsInBucket = 0;
    private int mediaFileLengthInMilliseconds;
    private final Handler handler = new Handler();
    final int[] currPos = new int[1];
    final AdapterView<?>[] view = new AdapterView<?>[1];
    int repeatSwiticher = 1;
    private String globalBucketName;
    private int selectedIndex = -1;
    private View previousViewItem = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playmusic);
        playMusicView = (ListView)findViewById(R.id.musicView);
        String bucketName = (String)getIntent().getSerializableExtra("BucketName");
        globalBucketName = bucketName;
        bucketManager = new BucketManager(bucketName);
        playPauseButton = (ImageButton)findViewById(R.id.playImageButton);
        playPauseButton.setImageResource(R.drawable.play_icon);

        repeatButton = (ImageButton)findViewById(R.id.repeatButton);
        repeatButton.setImageResource(R.drawable.offrepeat_icon);
        ImageButton backButton = (ImageButton)findViewById(R.id.backButton);
        backButton.setImageResource(R.drawable.back_icon);
        ImageButton forwardButton = (ImageButton)findViewById(R.id.forwardButton);
        forwardButton.setImageResource(R.drawable.forward_icon);

        currentDirectoryView = (TextView)findViewById(R.id.songName);
        currentDirectoryView.setText("  /");

        currPos[0] = -1;

        //Define listeners
        repeatButton.setOnClickListener(this);
        forwardButton.setOnClickListener(this);
        backButton.setOnClickListener(this);
        playPauseButton.setOnClickListener(this);

        new SpillBucketTask(bucketName, "").execute();
        playMusicView.setOnItemClickListener(this);

        seekBarProgress = (SeekBar)findViewById(R.id.seekBar);
        seekBarProgress.setMax(99); // It means 100% .0-99
        seekBarProgress.setOnTouchListener(this);

        player = new MediaPlayer();
        player.setOnBufferingUpdateListener(this);
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.playImageButton) {
            try {
                if (playPause.equals("Play")) {
                    processMusic();
                } else if (playPause.equals("UnPause")) {
                    playPause = "Pause";
                    playPauseButton.setImageResource(R.drawable.pause_icon);

                    player.start();
                } else if (playPause.equals("Pause")) {
                    playPause = "UnPause";
                    playPauseButton.setImageResource(R.drawable.play_icon);
                    player.pause();
                }

                primarySeekBarProgressUpdater();
            } catch (Exception e) {
                //Do Nothing
            }
        }else if(v.getId() == R.id.forwardButton){
            if (currPos[0] == -1){
                Toast.makeText(getApplicationContext(), "You must have a song selected to switch from.", Toast.LENGTH_LONG).show();
            }else{
                player.release();

                if (currPos[0] == (numItemsInBucket - 1)) {
                    currPos[0] = 0;
                } else {
                    currPos[0]++;
                }
                player = new MediaPlayer();
                player.setOnBufferingUpdateListener(this);
                try {
                    processMusic();
                }catch(Exception e){
                    e.printStackTrace();
                }
                playMusicView.setItemChecked(currPos[0], true);
            }
        }else if(v.getId() == R.id.backButton){
            if (currPos[0] == -1){
                Toast.makeText(getApplicationContext(), "You must have a song selected to switch from.", Toast.LENGTH_LONG).show();
            }else{
                player.release();

                if (currPos[0] == 0) {
                    currPos[0] = (numItemsInBucket - 1);
                } else {
                    currPos[0]--;
                }
                player = new MediaPlayer();
                player.setOnBufferingUpdateListener(this);
                try {
                    processMusic();
                }catch(Exception e){
                    e.printStackTrace();
                }
                playMusicView.setItemChecked(currPos[0], true);
            }
        }else if (v.getId() == R.id.repeatButton){
            if (repeatSwiticher == 1) {
                repeatButton.setImageResource(R.drawable.onrepeat_icon);
                repeatSwiticher = 2;
            } else if (repeatSwiticher == 2) {
                repeatButton.setImageResource(R.drawable.offrepeat_icon);
                repeatSwiticher = 1;
            } else {
                repeatButton.setImageResource(R.drawable.offrepeat_icon);
            }
        }
    }

    /** Method which updates the SeekBar primary progress by current song playing position*/
    private void primarySeekBarProgressUpdater() {
        seekBarProgress.setProgress((int)(((float)player.getCurrentPosition()/mediaFileLengthInMilliseconds)*100));
        // This math construction give a percentage of "was playing"/"song length"
        if (playPause.equals("Pause")) {
            Runnable notification = new Runnable() {
                public void run() {
                    primarySeekBarProgressUpdater();
                }
            };
            handler.postDelayed(notification, 1000);
        }
    }


    public void processMusic() throws Exception{
        String fileName = String.valueOf(view[0].getItemAtPosition(currPos[0]));
        //Toast.makeText(getApplicationContext(), "Make Toast!", Toast.LENGTH_SHORT).show();

        String originFile = "";
        if (fileName.contains("../")){
            String[] parse = currentDirectoryView.getText().toString().trim().split("/");
            String newDir = "";
            for (int i = 0; i < parse.length-1; i++){
                newDir+=parse[i] + "/";
            }
            currentDirectoryView.setText("  " + newDir);
        }else {
            if (currentDirectoryView.getText().toString().trim().equals("/")) {
                originFile = fileName + "";
            } else {
                String currDir = currentDirectoryView.getText().toString().trim();
                String dir = currDir.substring(1, currDir.length());
                originFile = dir + fileName + "";
            }
        }
        String url = bucketManager.getFileURL(originFile) + "";

        if (url.endsWith("mp3")){
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(url);
            playPause = "Pause";
            playPauseButton.setImageResource(R.drawable.pause_icon);
            player.setOnCompletionListener(this);
            Runnable r = new Runnable() {
                public void run(){
                    try{
                        player.prepare();
                    }catch (Exception e) {
                        //Do Nothing
                    }
                }
            };

            ExecutorService executeT1 = Executors.newFixedThreadPool(1);
            executeT1.execute(r);
            executeT1.shutdownNow();
            while (!executeT1.isTerminated()){}
            player.start();

            mediaFileLengthInMilliseconds = player.getDuration();
            primarySeekBarProgressUpdater();
        }else{
            playMusicView.setAdapter(null);
            String[] directories = url.split(globalBucketName + ".s3.amazonaws.com");
            String curDirectory = directories[1];
            currentDirectoryView.setText("  /" + originFile);
            String delim = curDirectory.substring(1, curDirectory.length());
            //Toast.makeText(getApplicationContext(), url + " " + delim, Toast.LENGTH_LONG).show();
           new SpillBucketTask(globalBucketName, delim).execute();
        }
    }


    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        seekBarProgress.setSecondaryProgress(percent);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        player.release();
        if (repeatSwiticher == 2){
            if (currPos[0] == (numItemsInBucket - 1)) {
                currPos[0] = 0;
            } else {
                currPos[0]++;
            }
            player = new MediaPlayer();
            player.setOnBufferingUpdateListener(this);
            try {
                processMusic();
            }catch(Exception e){
                e.printStackTrace();
            }
            playMusicView.setItemChecked(currPos[0], true);
        }else if (repeatSwiticher == 1){
            if (currPos[0] == (numItemsInBucket - 1)) {
                playPauseButton.setImageResource(R.drawable.play_icon);
                player = new MediaPlayer();
                player.setOnBufferingUpdateListener(this);

                playMusicView.clearChoices();
                playMusicView.requestLayout();
                playPause = "Play";
                currPos[0] = -1;
            } else {
                currPos[0]++;

                player = new MediaPlayer();
                player.setOnBufferingUpdateListener(this);
                try {
                    processMusic();
                }catch(Exception e){
                    e.printStackTrace();
                }
                playMusicView.setItemChecked(currPos[0], true);
            }

        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(v.getId() == R.id.seekBar){
            /** Seekbar onTouch event handler. Method which seeks MediaPlayer to seekBar primary progress position*/
            SeekBar sb = (SeekBar)v;
            int playPositionInMillisecconds = (mediaFileLengthInMilliseconds / 100) * sb.getProgress();
            player.seekTo(playPositionInMillisecconds);
        }
        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> parentView, View view1, int position, long id) {
        player.stop();
        player = new MediaPlayer();
        playPause = "Play";
        playPauseButton.setImageResource(R.drawable.play_icon);

        currPos[0] = position;
        view[0] = parentView;
        try{
            String fileName = String.valueOf(parentView.getItemAtPosition(position));


            //parentView.setBackgroundColor(Color.GREEN);
            if (fileName.endsWith("mp3")) {
                selectedIndex = position;
            }else{
                selectedIndex = -1;
            }

            view1.setBackgroundColor(Color.GRAY);

            processMusic();
            if (previousViewItem != null){
                previousViewItem.setBackgroundColor(Color.BLACK);
            }
            previousViewItem = view1;
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    class CustomPlayView extends ArrayAdapter<String> {
        public CustomPlayView(Context context, ArrayList<String> songNames){
            super(context, R.layout.custom_row, songNames);
        }

        public View getView(int position, View customView, ViewGroup parent) {

            if (customView == null) {
                LayoutInflater layoutInflator = LayoutInflater.from(getContext());
                customView = layoutInflator.inflate(R.layout.custom_row, null, true);
            }

            //Gets song name
            String song = getItem(position);
            TextView songName = (TextView) customView.findViewById(R.id.songName);
            ImageView image = (ImageView) customView.findViewById(R.id.musicIcon);

            songName.setText(song);
            if (song.endsWith("mp3")) {
                image.setImageResource(R.drawable.music);
            } else {
                image.setImageResource(R.drawable.folder);
            }

            customView.setBackgroundColor(Color.BLACK);


            if (selectedIndex != -1) {
                if (selectedIndex == position && getItem(selectedIndex).endsWith("mp3")) {
                    customView.setBackgroundColor(Color.GRAY);
                }
            }

            return customView;
        }
    }




    /** Inner class that acts as a way to spill the contents of a particular bucket onto the listView
     *  screen.
     *
     */
    public class SpillBucketTask extends AsyncTask<Void, Void, ArrayList<String>> {
        //Define instance variables
        private String bucketName;
        private String delim;

        public SpillBucketTask(String bucketName, String delim){
            this.bucketName = bucketName;
            this.delim = delim;
        }

        /** Method that runs when this task is executed. It lists the takes the objects of the
         *  bucket and lists the file names to have them placed in an Array.
         *
         * @param voids                         something...
         * @return                              return the list of items to be placed on the
         *                                      screen.
         */
        public ArrayList<String> doInBackground(Void... voids) {
            ArrayList<String> filesInBucket = null;
            try {
                if (delim.equals("")) {
                    filesInBucket = bucketManager.listObjectsInBucket(bucketName);
                }else{
                    filesInBucket = bucketManager.listObjectsInBucketWithDelim(bucketName, delim);
                }
            } catch (Exception e) {
                //Do nothing...
            }
            return filesInBucket;
        }

        /** After the execution of the method above, we wll then update our adapter to list the
         *  files on the screen and add that adapter onto the ListView to then show.
         *
         * @param filesInBucket                 files in bucket
         */
        public void onPostExecute(ArrayList<String> filesInBucket) {
            ArrayList<String> formatedFilesInBucket = new ArrayList<String>();
            if (!currentDirectoryView.getText().equals("  /")){
                formatedFilesInBucket.add("../ (go up one level)");
            }

            for (int i = 0; i < filesInBucket.size(); i++){
                String[] tok = filesInBucket.get(i).toString().split("/");

                if (!filesInBucket.get(i).toString().endsWith(".mp3") && tok.length == 1){
                    formatedFilesInBucket.add(filesInBucket.get(i));
                }
            }

            for (int i = 0; i < filesInBucket.size(); i++){
                if (filesInBucket.get(i).toString().endsWith(".mp3")){
                    formatedFilesInBucket.add(filesInBucket.get(i));
                }
            }

            ListAdapter list = new CustomPlayView(getApplicationContext(), formatedFilesInBucket);
            playMusicView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            playMusicView.setAdapter(list);
            numItemsInBucket = filesInBucket.size();
        }
    }
}
