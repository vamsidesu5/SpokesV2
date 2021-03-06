package vamsidesu5.com.spokesv2.ViewModel;

import android.arch.lifecycle.ViewModel;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import vamsidesu5.com.spokesv2.Model.Database;
import vamsidesu5.com.spokesv2.Model.User;

public class LoginViewModel extends ViewModel {
    private static final int RC_SIGN_IN = 123;
    private User currUser = User.getInstance();

    public List<AuthUI.IdpConfig> signInSetup() {
        List<String> permissions = new ArrayList<>();
        permissions.add("email");
        permissions.add("public_profile");
        permissions.add("user_friends");

        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.EmailBuilder().build(),
                new AuthUI.IdpConfig.FacebookBuilder().setPermissions(permissions).build());
        return providers;
    }

    public boolean checkSignedIn() {
        if (AccessToken.getCurrentAccessToken() != null) {
            currUser.updateUserInfo(FirebaseAuth.getInstance().getCurrentUser().getUid(), AccessToken.getCurrentAccessToken(), FirebaseAuth.getInstance().getCurrentUser());
            updateUserInfo();
            updateUserDir();
            updateFriendList();
            updateNumLogin();
            return true;
        } else {
            return false;
        }
    }

    public void loginSuccessLog(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String regToken = sharedPreferences.getString("token", "-1");
        currUser.updateUserInfo(FirebaseAuth.getInstance().getCurrentUser().getUid(), AccessToken.getCurrentAccessToken(), FirebaseAuth.getInstance().getCurrentUser());
        updateUserInfo();
        updateToken(regToken);
        updateUserDir();
        updateFriendList();
        updateNumLogin();
    }

    public boolean checkSetup() {
        return true;
    }

    private void updateUserInfo() {
        Database database = new Database("users/" + currUser.getFirebaseUserID());
        List<String> userInfoNodes = new ArrayList<>();
        userInfoNodes.add("email");
        userInfoNodes.add("name");
        userInfoNodes.add("numOfLogins");
        userInfoNodes.add("numPokes");
        List<Object> userInfoData = new ArrayList<>();
        userInfoData.add(currUser.getFirebaseUser().getEmail());
        userInfoData.add(currUser.getFirebaseUser().getDisplayName());
        userInfoData.add(0);
        userInfoData.add(0);
        database.updateChild(database.constructPayload(userInfoNodes, userInfoData));
    }

    private void updateUserDir() {
        Database database = new Database("dir/" + currUser.getFirebaseUserID());
        Map<String, Object> firebaseIDPayload = database.constructPayload(currUser.getFacebookToken().getUserId(), "facebookToken");
        database.updateChild(firebaseIDPayload);
        database.updateDatabasePath("dir/" + currUser.getFacebookToken().getUserId());
        Map<String, Object> facebookPayload = database.constructPayload(currUser.getFirebaseUserID(), "firebaseID");
        database.updateChild(facebookPayload);
    }

    private void updateToken(String token) {
        if (!token.equals("-1")) {
            Database database = new Database("users/" + currUser.getFirebaseUserID());
            database.updateChild(database.constructPayload("/token", token));
        }
    }

    private void updateNumLogin(){
        final DatabaseReference loginRef = FirebaseDatabase.getInstance().getReference("users/" + currUser.getFirebaseUserID());
        loginRef.child("numOfLogins").runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData mutableData) {
                Integer logins = mutableData.getValue(Integer.class);
                if (logins == null) {
                    return Transaction.success(mutableData);
                }
                mutableData.setValue(logins + 1);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError databaseError, boolean b, @Nullable DataSnapshot dataSnapshot) {

            }
        });
    }

    private void updateFriendList() {
        GraphRequest request = GraphRequest.newMeRequest(
                currUser.getFacebookToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        try {
                            JSONArray jsonArray = object.getJSONObject("friends").getJSONArray("data");
                            Database database = new Database("users/" + currUser.getFirebaseUserID() + "/friends");
                            for (int i = 0; i < jsonArray.length(); i++) {
                                String friendsName = jsonArray.getJSONObject(i).getString("name");
                                String friendId = jsonArray.getJSONObject(i).getString("id");
                                database.updateChild(database.constructPayload("/" + friendId, friendsName));
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
        Bundle parameters = new Bundle();
        parameters.putString("fields", "friends");
        request.setParameters(parameters);
        request.executeAsync();
    }

}
