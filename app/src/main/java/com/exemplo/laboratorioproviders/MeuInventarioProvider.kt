// ============================================================================
// ARQUIVO 2: MeuInventarioProvider.kt
// ----------------------------------------------------------------------------
// PAPEL NA ARQUITETURA: o "SERVIDOR" do modelo Cliente-Servidor (IPC).
// Outros apps (ou a própria MainActivity) NUNCA tocam no SQLite diretamente:
// eles pedem ao ContentResolver, que roteia a chamada até este Provider.
//
//   [Cliente] contentResolver.insert(URI, valores)
//        │
//        ▼
//   [Android] localiza o dono da authority "com.exemplo...inventario"
//        │
//        ▼
//   [MeuInventarioProvider.insert()] ──► [InventarioDbHelper / SQLite]
// ============================================================================
package com.exemplo.laboratorioproviders

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri

/**
 * Herdamos da classe abstrata ContentProvider e implementamos os
 * 6 MÉTODOS OBRIGATÓRIOS: onCreate, query, insert, update, delete e getType.
 */
class MeuInventarioProvider : ContentProvider() {

    // ------------------------------------------------------------------------
    // PARTE 1 — CONSTANTES E ENDEREÇOS (URIs)
    // ------------------------------------------------------------------------
    companion object {
        // AUTHORITY: nome único (como um "domínio") deste provider no aparelho.
        // DEVE ser idêntico ao android:authorities declarado no AndroidManifest.
        const val AUTHORITY = "com.exemplo.laboratorioproviders.inventario"

        // CONTENT_URI: endereço da tabela. Estrutura: content://authority/path
        // Uri.parse converte a String em objeto Uri.
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/produtos")

        // Códigos numéricos que o UriMatcher devolve para identificar
        // QUAL tipo de endereço foi pedido.
        private const val CODE_PRODUTOS = 1      // content://.../produtos     (tabela inteira)
        private const val CODE_PRODUTO_ID = 2    // content://.../produtos/5   (um registro)

        // UriMatcher: "porteiro" que valida se o endereço pedido pelo cliente
        // existe. NO_MATCH é o valor devolvido quando nenhum padrão casa.
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            // "produtos"   -> casa exatamente com a tabela
            addURI(AUTHORITY, "produtos", CODE_PRODUTOS)
            // "produtos/#" -> o "#" é curinga para QUALQUER número (o _id)
            // (extra além da apostila: permite operar em um único registro)
            addURI(AUTHORITY, "produtos/#", CODE_PRODUTO_ID)
        }
    }

    // lateinit: será inicializado em onCreate(), não no construtor.
    private lateinit var dbHelper: InventarioDbHelper

    // ------------------------------------------------------------------------
    // PARTE 2 — onCreate(): INICIALIZAÇÃO
    // ------------------------------------------------------------------------
    /**
     * Chamado pelo Android na INICIALIZAÇÃO DO PROCESSO do app, ANTES da
     * Application e das Activities. Por isso: seja rápido e não abra o banco
     * aqui (apenas crie o helper; o banco só abre no 1º readable/writableDatabase).
     *
     * @return true se inicializou com sucesso.
     */
    override fun onCreate(): Boolean {
        // `context` é nulo antes do attach, mas é garantido dentro de onCreate(),
        // por isso o `!!` é seguro aqui.
        dbHelper = InventarioDbHelper(context!!)
        return true
    }

    // ------------------------------------------------------------------------
    // PARTE 3 — query(): LEITURA (equivale ao SELECT)
    // ------------------------------------------------------------------------
    /**
     * @param uri           qual tabela/registro consultar
     * @param projection    colunas desejadas (null = todas)  -> "SELECT nome, quantidade"
     * @param selection     cláusula WHERE com "?" como marcadores -> "quantidade > ?"
     * @param selectionArgs valores que substituem os "?" (evita SQL Injection!)
     * @param sortOrder     ORDER BY -> "nome ASC"
     * @return Cursor: ponteiro leve que lê linha por linha, sem carregar tudo na RAM.
     */
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        // Banco só de leitura (mais leve que writableDatabase).
        val db = dbHelper.readableDatabase

        // Decide o que consultar conforme o tipo de URI recebida.
        val cursor = when (uriMatcher.match(uri)) {
            CODE_PRODUTOS -> db.query(
                InventarioDbHelper.TABELA_PRODUTOS,
                projection, selection, selectionArgs,
                null, null,      // groupBy e having (não usamos)
                sortOrder
            )
            CODE_PRODUTO_ID -> db.query(
                InventarioDbHelper.TABELA_PRODUTOS,
                projection,
                // Combina o filtro do cliente com "_id = ?" do registro pedido.
                filtroPorId(selection),
                argsPorId(uri, selectionArgs),
                null, null, sortOrder
            )
            // Segurança: URI que não conhecemos é rejeitada explicitamente.
            else -> throw IllegalArgumentException("URI desconhecida: $uri")
        }

        // Registra "esta URI é observada por este Cursor". Quando o Provider
        // chamar notifyChange(uri), quem estiver observando (ex.: uma lista na
        // tela) é avisado automaticamente -> PADRÃO OBSERVER do Módulo 2!
        cursor.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    // ------------------------------------------------------------------------
    // PARTE 4 — insert(): CRIAÇÃO (equivale ao INSERT)
    // ------------------------------------------------------------------------
    /**
     * @param values mapa chave-valor (coluna -> valor) do novo registro
     * @return a URI do registro criado, ex.: content://.../produtos/7
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Só faz sentido inserir na TABELA (não em um _id específico).
        if (uriMatcher.match(uri) != CODE_PRODUTOS) {
            throw IllegalArgumentException("URI inválida para inserção: $uri")
        }

        val db = dbHelper.writableDatabase
        // insert devolve o _id gerado, ou -1 em caso de falha.
        val id = db.insert(InventarioDbHelper.TABELA_PRODUTOS, null, values)

        if (id > 0) {
            // Monta o endereço do novo registro: CONTENT_URI + "/" + id
            val novaUri = ContentUris.withAppendedId(CONTENT_URI, id)
            // AVISA os observadores que os dados mudaram (OBSERVER em ação).
            context?.contentResolver?.notifyChange(novaUri, null)
            return novaUri
        }
        return null // falhou (ex.: "nome" ausente violou o NOT NULL)
    }

    // ------------------------------------------------------------------------
    // PARTE 5 — update(): ALTERAÇÃO (equivale ao UPDATE)
    // ------------------------------------------------------------------------
    /**
     * @return quantidade de linhas afetadas
     */
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val db = dbHelper.writableDatabase

        val linhas = when (uriMatcher.match(uri)) {
            // Atualiza as linhas que satisfazem o WHERE do cliente
            CODE_PRODUTOS -> db.update(
                InventarioDbHelper.TABELA_PRODUTOS, values, selection, selectionArgs
            )
            // Atualiza apenas o registro cujo _id está no fim da URI
            CODE_PRODUTO_ID -> db.update(
                InventarioDbHelper.TABELA_PRODUTOS, values,
                filtroPorId(selection), argsPorId(uri, selectionArgs)
            )
            else -> throw IllegalArgumentException("URI inválida para update: $uri")
        }

        // Só notifica se algo realmente mudou (evita redesenhos inúteis).
        if (linhas > 0) context?.contentResolver?.notifyChange(uri, null)
        return linhas
    }

    // ------------------------------------------------------------------------
    // PARTE 6 — delete(): REMOÇÃO (equivale ao DELETE)
    // ------------------------------------------------------------------------
    /**
     * @return quantidade de linhas removidas
     */
    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val db = dbHelper.writableDatabase

        val linhas = when (uriMatcher.match(uri)) {
            CODE_PRODUTOS -> db.delete(
                InventarioDbHelper.TABELA_PRODUTOS, selection, selectionArgs
            )
            CODE_PRODUTO_ID -> db.delete(
                InventarioDbHelper.TABELA_PRODUTOS,
                filtroPorId(selection), argsPorId(uri, selectionArgs)
            )
            else -> throw IllegalArgumentException("URI inválida para delete: $uri")
        }

        if (linhas > 0) context?.contentResolver?.notifyChange(uri, null)
        return linhas
    }

    // ------------------------------------------------------------------------
    // PARTE 7 — getType(): TIPO MIME
    // ------------------------------------------------------------------------
    /**
     * Informa o tipo MIME dos dados de uma URI. Convenção do Android:
     *  - "vnd.android.cursor.dir/..."  -> VÁRIOS registros (lista)
     *  - "vnd.android.cursor.item/..." -> UM registro
     * O sistema usa isso para decidir qual app pode abrir/tratar o dado.
     */
    override fun getType(uri: Uri): String? = when (uriMatcher.match(uri)) {
        CODE_PRODUTOS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.produtos"
        CODE_PRODUTO_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.produtos"
        else -> null
    }

    // ------------------------------------------------------------------------
    // FUNÇÕES AUXILIARES (privadas) — evitam repetir código nos métodos acima
    // ------------------------------------------------------------------------

    /** Junta o WHERE do cliente (se houver) com a condição "_id = ?". */
    private fun filtroPorId(selection: String?): String =
        if (selection.isNullOrEmpty()) "_id = ?" else "_id = ? AND ($selection)"

    /** Coloca o _id (último trecho da URI) como 1º argumento, seguido dos do cliente. */
    private fun argsPorId(uri: Uri, selectionArgs: Array<String>?): Array<String> =
        arrayOf(ContentUris.parseId(uri).toString()) + (selectionArgs ?: emptyArray())
}