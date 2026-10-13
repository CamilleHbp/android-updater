package studio.camille.updater;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public class TestHostActivity extends Activity {
    @Override public void onCreate(Bundle state) { super.onCreate(state); TextView view = new TextView(this); view.setText("Updater test host"); setContentView(view); }
}
