package ch.ethz.twimight;



import winterwell.jtwitter.Twitter.Status;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DbOpenHelper extends SQLiteOpenHelper {
	  Context context;
	  static final String TAG = "DbHelper";
	  static final String DB_NAME = "timeline.db";
	  static final int    DB_VERSION = 14;
	  //TABLES
	  static final String TABLE = "timeline"; 
	  static final String TABLE_DISASTER = "DisasterTable";
	  static final String TABLE_ADDRESSES = "AddressesTable";
	  static final String TABLE_FAVORITES = "FavoritesTable";  
	  static final String TABLE_MENTIONS = "MentionsTable";
	  static final String TABLE_PICTURES = "PicturesTable";
	  static final String TABLE_DIRECT = "DirectMessagesTable";
	  static final String TABLE_SEARCH = "SearchTable";
	  public static final String TABLE_FRIENDS = "FriendsTable";	
	  public static final String TABLE_DIRECT_OUTGOING = "DirectOutgoingTable";
	  
	  //COLUMN NAMES
	  static final String C_MAC = "_mac";  
	  static final String C_MET_AT = "met_at";
	  static final String C_TWEETS_NUMBER = "tweetsNumber";
	  static final String C_ADDED_AT = "added_at";
	  public static final String C_ID = "_id";
	  public static final String C_CREATED_AT = "created_at";
	  public static final String C_TEXT = "status";
	  public static final String C_USER = "user";
	  public static final String C_USER_ID = "userCode";	
	  public static final String C_USER_RECIPIENT = "recipientUser";
	  static final String C_SENT_BY = "sent_by";
	  static final String C_IS_FAVORITE = "isFavorite";
	  public static final String C_IS_DISASTER = "isDisaster";
	  public static final String C_IS_DISASTER_FRIEND = "isDisasterFriend";	  
	  public static final String C_IS_MY_FOLLOWER = "isFollower";
	  public static final String C_IS_FOLLOWED_BY_ME = "isFollowed";
	  static final String C_ISFROMSERVER = "isFromServer";
	  public static final String C_HASBEENSENT = "hasBeenSent";
	  static final String C_IS_VALID = "isValid";
	  static final String C_HOPCOUNT = "hopCount";
	  public static final String C_IMAGE = "image";
	  public static final String C_MODULUS = "modulus";
	  public static final String C_EXPONENT = "exponent";	 
	  public static final String C_SIGNATURE = "signature";
	  
	  String sqlDisaster,sqlAddresses,sqlFavorites,sqlMentions,sql, sqlPictures,
	  		sqlDirect, sqlSearch, sqlFriends, sqlDirectOutgoing;
	  

  /** Constructor for DbHelper */
  public DbOpenHelper(Context context) {
    super(context, DB_NAME, null, DB_VERSION);
    this.context = context;
  }

  
  private void createTables(SQLiteDatabase db) {
	    sql = context.getResources().getString(R.string.sql);
	    // String sql2 = context.getResources().getString(R.string.sql2);
	    sqlDisaster = context.getResources().getString(R.string.sqlDisaster);
	    sqlAddresses = context.getResources().getString(R.string.sqlAddresses);
	    sqlFavorites = context.getResources().getString(R.string.sqlFavorites);
	    sqlMentions = context.getResources().getString(R.string.sqlMentions);
	    sqlPictures = context.getResources().getString(R.string.sqlProfilePictures);
	    sqlDirect = context.getResources().getString(R.string.sqlDirect);
	    sqlSearch = context.getResources().getString(R.string.sqlSearch);
	    sqlFriends = context.getResources().getString(R.string.sqlFriends);
	    sqlDirectOutgoing = context.getResources().getString(R.string.sqlDirectOutgoing);
	    db.execSQL(sql); // execute the sql
	    db.execSQL(sqlSearch); 
	    db.execSQL(sqlDisaster); 
	    db.execSQL(sqlAddresses); 
	    db.execSQL(sqlFavorites); 
	    db.execSQL(sqlMentions);  
	    db.execSQL(sqlPictures);
	    db.execSQL(sqlDirect);
	    db.execSQL(sqlFriends);
	    db.execSQL(sqlDirectOutgoing);
}
  
  /** Called only once, first time database is created */
  @Override
  public void onCreate(SQLiteDatabase db) {   
    createTables(db);
    Log.d(TAG, "onCreate'd sql: " + sql);
  }

  /** Called every time DB version changes */
  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.d(TAG, String
            .format("onUpgrade from %s to %s", oldVersion, newVersion));
    // temporary
    db.execSQL("DROP TABLE IF EXISTS timeline;");
    db.execSQL("DROP TABLE IF EXISTS SearchTable;");
    db.execSQL("DROP TABLE IF EXISTS DisasterTable;");
    db.execSQL("DROP TABLE IF EXISTS AddressesTable;");
    db.execSQL("DROP TABLE IF EXISTS FavoritesTable;");
    db.execSQL("DROP TABLE IF EXISTS MentionsTable;");
    db.execSQL("DROP TABLE IF EXISTS PicturesTable;");
    db.execSQL("DROP TABLE IF EXISTS DirectMessagesTable;");
    db.execSQL("DROP TABLE IF EXISTS FriendsTable;");
    db.execSQL("DROP TABLE IF EXISTS DirectOutgoingTable;");
    createTables(db);
  }
  
  /** Converts Twitter.Status to ContentValues */
  public static ContentValues statusToContentValues(Status status, Long userId) {
    ContentValues ret = new ContentValues();
    ret.put(C_ID, status.id.longValue()); 
    ret.put(C_CREATED_AT, status.getCreatedAt().getTime());
    ret.put(C_TEXT, status.getText());
    //ret.put(C_USER, status.getUser().getScreenName());
    if (userId == null)
    	ret.put(C_USER_ID, status.getUser().getId().longValue());
    else
    	ret.put(C_USER_ID, userId);
    
    return ret;
  }
}