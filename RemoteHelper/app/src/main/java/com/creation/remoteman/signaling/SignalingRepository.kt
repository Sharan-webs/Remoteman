package com.creation.remoteman.signaling

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.random.Random

/**
 * All signaling data for one session lives at: sessions/{code}
 *   sessions/{code}/offer       -> SDP from the host (helper)
 *   sessions/{code}/answer      -> SDP from the connector (needer)
 *   sessions/{code}/candidates/host/{pushId}      -> ICE candidates from host
 *   sessions/{code}/candidates/connector/{pushId} -> ICE candidates from connector
 *   sessions/{code}/status      -> "waiting" | "connected" | "ended"
 *
 * The code itself is the "room name" both sides agree on out of band.
 */
class SignalingRepository {

    private val db = FirebaseDatabase.getInstance()

    fun generateCode(): String =
        (100000 + Random.nextInt(900000)).toString()

    fun sessionRef(code: String) = db.getReference("sessions/$code")

    fun createSession(code: String, onDone: (Boolean) -> Unit) {
        sessionRef(code).child("status").setValue("waiting")
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun sendOffer(code: String, sdp: String) {
        sessionRef(code).child("offer").setValue(sdp)
    }

    fun sendAnswer(code: String, sdp: String) {
        sessionRef(code).child("answer").setValue(sdp)
        sessionRef(code).child("status").setValue("connected")
    }

    fun sendCandidate(code: String, role: String, candidate: String) {
        sessionRef(code).child("candidates").child(role).push().setValue(candidate)
    }

    fun listenForAnswer(code: String, onAnswer: (String) -> Unit) {
        sessionRef(code).child("answer").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(String::class.java)?.let(onAnswer)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun listenForOffer(code: String, onOffer: (String) -> Unit) {
        sessionRef(code).child("offer").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(String::class.java)?.let(onOffer)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun listenForCandidates(code: String, fromRole: String, onCandidate: (String) -> Unit) {
        sessionRef(code).child("candidates").child(fromRole)
            .addChildEventListener(object : com.google.firebase.database.ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    snapshot.getValue(String::class.java)?.let(onCandidate)
                }
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /** Fires onConnected when a connector's answer appears (host side "green" indicator). */
    fun listenForHelperJoined(code: String, onConnected: () -> Unit) {
        sessionRef(code).child("status").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(String::class.java) == "connected") onConnected()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun endSession(code: String) {
        sessionRef(code).child("status").setValue("ended")
        sessionRef(code).removeValue()
    }
}
