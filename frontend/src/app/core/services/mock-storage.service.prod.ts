import { Injectable } from '@angular/core';
import { IMockStorage } from './mock-storage.contract';

/**
 * Implementação de PRODUÇÃO: substitui mock-storage.service.ts via
 * `fileReplacements` (ver angular.json, configuração "production"). Não
 * contém nenhum cliente, tarefa, venda, despesa, avaliação, colaborador ou
 * credencial fictícia — nada disso é compilado no bundle final.
 *
 * `ApiService.execute()` só chama o fallback local quando
 * `!environment.production`, então nenhum destes métodos deveria ser
 * executado em produção. Caso algum caminho de código chegue aqui mesmo
 * assim, o erro é explícito em vez de inventar uma resposta.
 */
@Injectable({
  providedIn: 'root',
})
export class MockStorageService implements IMockStorage {
  private indisponivel(): never {
    throw new Error(
      'Dados de demonstração não estão disponíveis em produção. Verifique a conexão com a API.'
    );
  }

  getDashboardStats() { return this.indisponivel(); }
  getSaudeFinanceira() { return this.indisponivel(); }

  getClientes() { return this.indisponivel(); }
  getClienteById() { return this.indisponivel(); }
  createCliente() { return this.indisponivel(); }
  updateCliente() { return this.indisponivel(); }
  deleteCliente() { return this.indisponivel(); }

  getFaturas() { return this.indisponivel(); }
  createFatura() { return this.indisponivel(); }
  updateFatura() { return this.indisponivel(); }
  toggleStatusFatura() { return this.indisponivel(); }
  deleteFatura() { return this.indisponivel(); }

  getServicos() { return this.indisponivel(); }
  createServico() { return this.indisponivel(); }
  updateServico() { return this.indisponivel(); }
  deleteServico() { return this.indisponivel(); }

  getColaboradores() { return this.indisponivel(); }
  createColaborador() { return this.indisponivel(); }
  updateColaborador() { return this.indisponivel(); }
  deleteColaborador() { return this.indisponivel(); }

  getAvaliacoes() { return this.indisponivel(); }
  createAvaliacao() { return this.indisponivel(); }
  updateAvaliacao() { return this.indisponivel(); }
  deleteAvaliacao() { return this.indisponivel(); }

  getMunicipios() { return this.indisponivel(); }
  createMunicipio() { return this.indisponivel(); }
  deleteMunicipio() { return this.indisponivel(); }

  getConfigLoja() { return this.indisponivel(); }
  updateConfigLoja() { return this.indisponivel(); }

  getHistorico() { return this.indisponivel(); }

  getTarefas() { return this.indisponivel(); }
  getTarefaById() { return this.indisponivel(); }
  getTarefaByToken() { return this.indisponivel(); }
  createTarefa() { return this.indisponivel(); }
  updateTarefa() { return this.indisponivel(); }
  deleteTarefa() { return this.indisponivel(); }
  addObservacaoTarefa() { return this.indisponivel(); }
  addArquivoFinal() { return this.indisponivel(); }
  removeArquivoFinal() { return this.indisponivel(); }
  responderAprovacao() { return this.indisponivel(); }

  getRoteiros() { return this.indisponivel(); }
  getRoteiroById() { return this.indisponivel(); }
  createRoteiro() { return this.indisponivel(); }
  updateRoteiro() { return this.indisponivel(); }
  toggleGravacaoRoteiro() { return this.indisponivel(); }
  deleteRoteiro() { return this.indisponivel(); }

  getLogos() { return this.indisponivel(); }
  createLogo() { return this.indisponivel(); }
  deleteLogo() { return this.indisponivel(); }

  getEventos() { return this.indisponivel(); }
  getEventoById() { return this.indisponivel(); }
  createEvento() { return this.indisponivel(); }
  addFotoEvento() { return this.indisponivel(); }
  deleteFotoEvento() { return this.indisponivel(); }
  deleteEvento() { return this.indisponivel(); }

  getVendas() { return this.indisponivel(); }
  createVenda() { return this.indisponivel(); }

  getDespesas() { return this.indisponivel(); }
  createDespesa() { return this.indisponivel(); }
  deleteDespesa() { return this.indisponivel(); }

  getRelatorioMensal() { return this.indisponivel(); }
  getFluxoCaixa() { return this.indisponivel(); }
}
