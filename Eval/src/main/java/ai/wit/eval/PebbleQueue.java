package ai.wit.eval;

import android.content.Context;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by oliv on 11/9/13.
 */
public class PebbleQueue {
    private ConcurrentLinkedQueue<PebbleDictionary> _queue;
    private Context _context;
    private UUID _pebbleAppId;
    private int _transactionIds = 0;
    private int _currentTransactionId = -1;
    private long _lastSend = 0;

    public PebbleQueue(Context context, UUID pebbleAppId) {
        _queue = new ConcurrentLinkedQueue<PebbleDictionary>();
        _context = context;
        _pebbleAppId = pebbleAppId;
        _lastSend = System.currentTimeMillis();
        timer.schedule(new PeriodicTask(), 4000, 4000);
    }

    public void Enqueue(PebbleDictionary msg) {
        _queue.add(msg);
        TrySendNextMessage();

    }

    public void TrySendNextMessage() {
        if (_currentTransactionId == -1 && _queue.peek() != null) {
            _currentTransactionId = _transactionIds++;
            _lastSend = System.currentTimeMillis();
            Log.d("Wit", "Sending message to Pebble... ");
            PebbleKit.sendDataToPebbleWithTransactionId(_context, _pebbleAppId, _queue.poll(), _currentTransactionId);
        }
    }

    public void Ack(int transactionId) {
        _currentTransactionId = -1;
        TrySendNextMessage();
    }

    private Timer timer = new Timer();

    private class PeriodicTask extends TimerTask {
        @Override
        public void run() {
            if (_currentTransactionId != -1 && _queue.peek() != null && (System.currentTimeMillis() - _lastSend) > 5000) {
                Log.d("Wit", "Timeout " + (System.currentTimeMillis() - _lastSend));
                //Auto-Ack
                Ack(0);
            }
        }
    }
}
