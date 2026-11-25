// ============================================================================
// ARQUIVO 1: InventarioDbHelper.kt
// ----------------------------------------------------------------------------
// PAPEL NA ARQUITETURA: camada de PERSISTÊNCIA (banco SQLite local).
// Esta classe sabe criar o banco e a tabela. Ela NÃO conhece Content Providers:
// quem expõe os dados para outros apps é o MeuInventarioProvider (Arquivo 2).
// ============================================================================
package com.exemplo.laboratorioproviders

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLiteOpenHelper é a classe oficial do Android para gerenciar o ciclo de vida
 * de um banco SQLite: criar na primeira vez, atualizar quando a versão muda e
 * entregar conexões de leitura/escrita.
 *
 * Parâmetros repassados ao construtor da classe pai:
 *  - context   : necessário para o Android saber ONDE gravar o arquivo do banco
 *                (pasta privada do app -> protegida pela Sandbox).
 *  - "estoque.db": nome do arquivo físico do banco.
 *  - null      : CursorFactory (não precisamos de um customizado).
 *  - 1         : versão do esquema. Se aumentar este número, onUpgrade() é chamado.
 */
class InventarioDbHelper(context: Context) :
    SQLiteOpenHelper(context, NOME_BANCO, null, VERSAO_BANCO) {

    // Constantes centralizadas: evita "strings mágicas" espalhadas pelo código
    // e erros de digitação. O Provider reutiliza TABELA_PRODUTOS.
    companion object {
        const val NOME_BANCO = "estoque.db"
        const val VERSAO_BANCO = 1
        const val TABELA_PRODUTOS = "produtos"
    }

    /**
     * Chamado UMA ÚNICA VEZ: quando o arquivo estoque.db ainda não existe.
     * Aqui criamos a estrutura (esquema) das tabelas.
     */
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABELA_PRODUTOS (" +
                    // _id é o nome PADRÃO do Android para a chave primária.
                    // Adapters e CursorLoaders esperam exatamente esse nome.
                    // AUTOINCREMENT faz o banco gerar 1, 2, 3... sozinho.
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    // NOT NULL: o banco rejeita produtos sem nome.
                    "nome TEXT NOT NULL, " +
                    // Quantidade em estoque (também obrigatória).
                    "quantidade INTEGER NOT NULL)"
        )
    }

    /**
     * Chamado quando VERSAO_BANCO aumenta (ex.: de 1 para 2).
     * Estratégia simples (didática): apaga a tabela e recria do zero.
     * ATENÇÃO: isso APAGA os dados! Em produção, use ALTER TABLE para migrar
     * os dados sem perdê-los.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABELA_PRODUTOS")
        onCreate(db)
    }
}