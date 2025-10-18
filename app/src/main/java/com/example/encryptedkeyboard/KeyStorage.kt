package com.example.encryptedkeyboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Contact(
    val name: String,
    val publicKey: String
)

class KeyStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("encrypted_keyboard_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_CONTACTS = "contacts"
    }

    fun saveKeyPair(publicKey: String, privateKey: String) {
        prefs.edit().apply {
            putString(KEY_PUBLIC_KEY, publicKey)
            putString(KEY_PRIVATE_KEY, privateKey)
            apply()
        }
    }

    fun getPublicKey(): String? {
        return prefs.getString(KEY_PUBLIC_KEY, null)
    }

    fun getPrivateKey(): String? {
        return prefs.getString(KEY_PRIVATE_KEY, null)
    }

    fun hasKeys(): Boolean {
        return getPublicKey() != null && getPrivateKey() != null
    }

    fun addContact(name: String, publicKey: String) {
        val contacts = getAllContacts().toMutableList()
        contacts.removeAll { it.name == name }
        contacts.add(Contact(name, publicKey))
        saveContacts(contacts)
    }

    fun removeContact(name: String) {
        val contacts = getAllContacts().toMutableList()
        contacts.removeAll { it.name == name }
        saveContacts(contacts)
    }

    fun getAllContacts(): List<Contact> {
        val contactsJson = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()

        val contacts = mutableListOf<Contact>()
        val jsonArray = JSONArray(contactsJson)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            contacts.add(Contact(
                name = obj.getString("name"),
                publicKey = obj.getString("publicKey")
            ))
        }

        return contacts
    }

    fun getContact(name: String): Contact? {
        return getAllContacts().firstOrNull { it.name == name }
    }

    private fun saveContacts(contacts: List<Contact>) {
        val jsonArray = JSONArray()

        contacts.forEach { contact ->
            val obj = JSONObject().apply {
                put("name", contact.name)
                put("publicKey", contact.publicKey)
            }
            jsonArray.put(obj)
        }

        prefs.edit().putString(KEY_CONTACTS, jsonArray.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}