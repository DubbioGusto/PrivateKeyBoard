package com.example.encryptedkeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class KeyManagementActivity : AppCompatActivity() {

    private lateinit var keyStorage: KeyStorage
    private lateinit var cryptoHelper: CryptoHelper

    private lateinit var publicKeyText: TextView
    private lateinit var generateKeyButton: Button
    private lateinit var copyPublicKeyButton: Button
    private lateinit var addContactButton: Button
    private lateinit var contactsListView: ListView

    private val contactsList = mutableListOf<Contact>()
    private lateinit var contactsAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_key_management)

        keyStorage = KeyStorage(this)
        cryptoHelper = CryptoHelper()

        initViews()
        loadData()
        setupListeners()
    }

    private fun initViews() {
        publicKeyText = findViewById(R.id.public_key_text)
        generateKeyButton = findViewById(R.id.generate_key_button)
        copyPublicKeyButton = findViewById(R.id.copy_public_key_button)
        addContactButton = findViewById(R.id.add_contact_button)
        contactsListView = findViewById(R.id.contacts_list)

        contactsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        contactsListView.adapter = contactsAdapter
    }

    private fun loadData() {
        val publicKey = keyStorage.getPublicKey()
        if (publicKey != null) {
            publicKeyText.text = formatKey(publicKey)
            copyPublicKeyButton.isEnabled = true
        } else {
            publicKeyText.text = "No key generated yet"
            copyPublicKeyButton.isEnabled = false
        }

        refreshContactsList()
    }

    private fun refreshContactsList() {
        contactsList.clear()
        contactsList.addAll(keyStorage.getAllContacts())

        val displayList = contactsList.map { it.name }
        contactsAdapter.clear()
        contactsAdapter.addAll(displayList)
        contactsAdapter.notifyDataSetChanged()
    }

    private fun setupListeners() {
        generateKeyButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Generate New Key Pair")
                .setMessage("This will replace your existing keys. You won't be able to decrypt old messages. Continue?")
                .setPositiveButton("Generate") { _, _ ->
                    generateNewKeyPair()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        copyPublicKeyButton.setOnClickListener {
            val publicKey = keyStorage.getPublicKey() ?: return@setOnClickListener
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Public Key", publicKey)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Public key copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        addContactButton.setOnClickListener {
            showAddContactDialog()
        }

        contactsListView.setOnItemLongClickListener { _, _, position, _ ->
            val contact = contactsList[position]
            AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Delete ${contact.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    keyStorage.removeContact(contact.name)
                    refreshContactsList()
                    Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        contactsListView.setOnItemClickListener { _, _, position, _ ->
            val contact = contactsList[position]
            showContactDetailsDialog(contact)
        }
    }

    private fun generateNewKeyPair() {
        try {
            val keyPair = cryptoHelper.generateKeyPair()
            val publicKeyString = cryptoHelper.publicKeyToString(keyPair.public)
            val privateKeyString = cryptoHelper.privateKeyToString(keyPair.private)

            keyStorage.saveKeyPair(publicKeyString, privateKeyString)
            loadData()

            Toast.makeText(this, "Key pair generated successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error generating keys: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.contact_name_input)
        val publicKeyInput = dialogView.findViewById<EditText>(R.id.contact_public_key_input)
        val pasteButton = dialogView.findViewById<Button>(R.id.paste_button)

        pasteButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pastedText = clipData.getItemAt(0).text.toString()
                publicKeyInput.setText(pastedText)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Add Contact")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val publicKey = publicKeyInput.text.toString().trim()

                if (name.isEmpty() || publicKey.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                try {
                    cryptoHelper.stringToPublicKey(publicKey)
                    keyStorage.addContact(name, publicKey)
                    refreshContactsList()
                    Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid public key format", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showContactDetailsDialog(contact: Contact) {
        val message = "Name: ${contact.name}\n\nPublic Key:\n${formatKey(contact.publicKey)}"

        AlertDialog.Builder(this)
            .setTitle("Contact Details")
            .setMessage(message)
            .setPositiveButton("Copy Key") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Public Key", contact.publicKey)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Public key copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun formatKey(key: String): String {
        return if (key.length > 50) {
            "${key.substring(0, 50)}..."
        } else {
            key
        }
    }
}