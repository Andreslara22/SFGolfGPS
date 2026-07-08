package mx.clubsanfrancisco.golfgps

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Cuenta opcional + respaldo en la nube (Firebase Auth + Firestore).
 *
 * Solo funciona si el proyecto tiene `app/google-services.json` (Firebase
 * configurado). Sin él, [isConfigured] devuelve false, la UI de cuenta no se
 * muestra y la app trabaja 100% local como siempre.
 */
object Cloud {

    fun isConfigured(context: Context): Boolean =
        FirebaseApp.getApps(context).isNotEmpty()

    fun currentEmail(): String? =
        runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull()

    private fun uid(): String? =
        runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()

    /** Inicia sesión. cb(null) = éxito, cb(mensaje) = error legible. */
    fun signIn(email: String, password: String, cb: (String?) -> Unit) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { cb(null) }
            .addOnFailureListener { cb(it.localizedMessage ?: "No se pudo iniciar sesión") }
    }

    /** Crea la cuenta e inicia sesión. */
    fun signUp(email: String, password: String, cb: (String?) -> Unit) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { cb(null) }
            .addOnFailureListener { cb(it.localizedMessage ?: "No se pudo crear la cuenta") }
    }

    /** Envía el correo de restablecer contraseña. */
    fun sendPasswordReset(email: String, cb: (String?) -> Unit) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { cb(null) }
            .addOnFailureListener { cb(it.localizedMessage ?: "No se pudo enviar el correo") }
    }

    fun signOut() {
        runCatching { FirebaseAuth.getInstance().signOut() }
    }

    /** Sube el respaldo del usuario a users/{uid}. */
    fun backup(data: Map<String, Any?>, cb: (String?) -> Unit) {
        val id = uid() ?: return cb("Sin sesión")
        FirebaseFirestore.getInstance().collection("users").document(id)
            .set(data)
            .addOnSuccessListener { cb(null) }
            .addOnFailureListener { cb(it.localizedMessage ?: "No se pudo respaldar") }
    }

    /** Baja el respaldo del usuario. cb(datos, error). */
    fun restore(cb: (Map<String, Any?>?, String?) -> Unit) {
        val id = uid() ?: return cb(null, "Sin sesión")
        FirebaseFirestore.getInstance().collection("users").document(id)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) cb(doc.data, null)
                else cb(null, "No hay respaldo guardado todavía")
            }
            .addOnFailureListener { cb(null, it.localizedMessage ?: "No se pudo restaurar") }
    }
}
