// ============================================================================
// ARQUIVO 4: MainActivity.kt  (Passo 4 — Testando o Provider)
// ----------------------------------------------------------------------------
// A Activity age como CLIENTE: usa SOMENTE o contentResolver e a URI.
// Ela nunca importa InventarioDbHelper nem escreve SQL.
// ============================================================================
package com.exemplo.laboratorioproviders

import android.content.ContentUris
import android.content.ContentValues
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        testarProviderPersonalizado()
    }

    private fun testarProviderPersonalizado() {
        // Acesso a disco = operação lenta. Rodamos em Dispatchers.IO para
        // NUNCA bloquear a Main Thread (evita ANR). O lifecycleScope cancela
        // a corrotina sozinho se a Activity for destruída.
        lifecycleScope.launch(Dispatchers.IO) {

            // ---------------- 1) INSERT ----------------
            // ContentValues = mapa coluna -> valor (o "_id" é gerado sozinho).
            val valores = ContentValues().apply {
                put("nome", "Teclado Mecânico")
                put("quantidade", 15)
            }
            // Devolve a URI do registro criado (ex.: content://.../produtos/1)
            val uriCriada = contentResolver.insert(MeuInventarioProvider.CONTENT_URI, valores)
            Log.i("PROVIDER_CUSTOM", "Inserido em: $uriCriada")

            // ---------------- 2) QUERY ----------------
            // `?.use { }` fecha o Cursor automaticamente no fim do bloco,
            // mesmo se der exceção (evita vazamento de Cursor).
            contentResolver.query(
                MeuInventarioProvider.CONTENT_URI,
                arrayOf("_id", "nome", "quantidade"), // projection: só as colunas necessárias
                null, null,                           // sem filtro WHERE
                "nome ASC"                            // ordenação
            )?.use { cursor ->
                // Descobre o índice de cada coluna UMA vez, fora do laço (mais rápido).
                val iId = cursor.getColumnIndexOrThrow("_id")
                val iNome = cursor.getColumnIndexOrThrow("nome")
                val iQtd = cursor.getColumnIndexOrThrow("quantidade")

                // moveToNext() avança linha a linha; devolve false no fim.
                while (cursor.moveToNext()) {
                    Log.i(
                        "PROVIDER_CUSTOM",
                        "Produto #${cursor.getLong(iId)}: " +
                                "${cursor.getString(iNome)} (qtd=${cursor.getInt(iQtd)})"
                    )
                }
            }

            // ---------------- 3) UPDATE (desafio extra) ----------------
            if (uriCriada != null) {
                val novos = ContentValues().apply { put("quantidade", 20) }
                // A URI já aponta para o registro específico (.../produtos/1)
                val linhasAtualizadas = contentResolver.update(uriCriada, novos, null, null)
                Log.i("PROVIDER_CUSTOM", "Linhas atualizadas: $linhasAtualizadas")

                // ---------------- 4) DELETE (desafio extra) ----------------
                // Descomente para apagar o registro e ver sumir no Database Inspector:
                // val removidas = contentResolver.delete(uriCriada, null, null)
                // Log.i("PROVIDER_CUSTOM", "Linhas removidas: $removidas")
            }
        }
    }
}

// COMO VALIDAR:
// 1. Run 'app' no Emulador.
// 2. Logcat -> filtre por PROVIDER_CUSTOM.
// 3. View > Tool Windows > App Inspection > Database Inspector:
//    selecione estoque.db > tabela "produtos" e veja os dados em tempo real.