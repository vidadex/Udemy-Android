package matthewallenlinsoftware.hackernewsreadermatt;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends Activity {
    Map<Integer, String> articleURLs = new HashMap<Integer, String>();
    Map<Integer, String> articleTitles = new HashMap<Integer, String>();
    ArrayList<Integer> articleIds = new ArrayList<Integer>();

    SQLiteDatabase articlesDB;
    ArrayList<String> titles = new ArrayList<String>();
    ArrayAdapter arrayAdapter;

    ArrayList<String> urls = new ArrayList<String>();
    ArrayList<String> content = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView listView = (ListView) findViewById(R.id.listView);
        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(getApplicationContext(), ArticleActivity.class);
                i.putExtra("articleUrl", urls.get(position));
                i.putExtra("content", content.get(position));
                startActivity(i);
            }
        });

        articlesDB = this.openOrCreateDatabase("Articles", MODE_PRIVATE, null);

        articlesDB.execSQL("CREATE TABLE IF NOT EXISTS articles (id INTEGER PRIMARY KEY, articleId INTEGER, url VARCHAR, title VARCHAR, content VARCHAR)");

        updateListView();

        DownloadTask task = new DownloadTask();

        try {
                task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty").get();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void updateListView() {

        try {
            Log.i("UI UPDATED", "DONE");

            //Returns all the articles in order by ID with the biggest ones first
            Cursor c = articlesDB.rawQuery("SELECT * FROM articles ORDER BY articleId DESC", null);

            int contentIndex = c.getColumnIndex("articleId");
            int urlIndex = c.getColumnIndex("url");
            int titleIndex = c.getColumnIndex("title");

            c.moveToFirst();

            titles.clear(); //Remove items from ArrayList titles
            urls.clear();   //Removes items from ArrayList urls

            while(c != null) {
                titles.add(c.getString(titleIndex));
                urls.add(c.getString(urlIndex));
                content.add(c.getString(contentIndex));

                c.moveToNext();
            }

            arrayAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {
            String result = "";
            URL url;
            HttpURLConnection urlConnection = null;

            try {
                url = new URL(urls[0]); //Get the 1st element from var args

                urlConnection = (HttpURLConnection) url.openConnection();

                InputStream in = urlConnection.getInputStream();

                InputStreamReader reader = new InputStreamReader(in);

                int data = reader.read();

                while(data != -1) {
                    char current = (char) data;

                    result += current;

                    data = reader.read();
                }
                //
                JSONArray jsonArray = new JSONArray(result);

                articlesDB.execSQL("DELETE FROM articles"); //Delete everything from articles so we don't have duplicates

                for (int i = 0; i < 20; i++) {
                    String articleId = jsonArray.getString(i);

                    url = new URL("https://hacker-news.firebaseio.com/v0/item/" + articleId + ".json?print=pretty");

                    urlConnection = (HttpURLConnection) url.openConnection();

                    in = urlConnection.getInputStream();

                    reader = new InputStreamReader(in);

                    data = reader.read();

                    String articleContent = "", articleInfo = "";

                    //Should have article info that is processed in the same way as before
                    while(data != -1) {
                        char current = (char) data;

                        articleInfo += current;

                        data = reader.read();
                    }

                    JSONObject jsonObject = new JSONObject(articleInfo);

                    String articleTitle = jsonObject.getString("title");
                    String articleURL = jsonObject.getString("url");

                    url = new URL(articleURL);

                    urlConnection = (HttpURLConnection) url.openConnection();

                    in = urlConnection.getInputStream();

                    reader = new InputStreamReader(in);

                    data = reader.read();

                    articleIds.add(Integer.valueOf(articleId));
                    articleTitles.put(Integer.valueOf(articleId), articleTitle);
                    articleURLs.put(Integer.valueOf(articleId), articleURL);

                    String sql = "INSERT INTO articles (articleId, url, title, content) VALUES (? , ? , ? , ? )";

                    SQLiteStatement statement = articlesDB.compileStatement(sql);   //Turn it from String -> SQL statement

                    //Don't worry about ? and protects you from SQL injection code
                    statement.bindString(1, articleId);
                    statement.bindString(2, articleURL);
                    statement.bindString(3, articleTitle);
                    statement.bindString(4, articleContent);

                    //articlesDB.execSQL();
                    statement.execute();    //Safer way of doing the comment above
                }

                updateListView();
            } catch(Exception e) {
                e.printStackTrace();
            }

            return result;
        }

        @Override
        protected  void onPostExecute(String s) {
            super.onPostExecute(s);

            updateListView();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
