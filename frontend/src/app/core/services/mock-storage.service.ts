import { Injectable } from '@angular/core';
import {
  Tarefa,
  Roteiro,
  LogoCliente,
  Evento,
  FotoEvento,
  VendaFoto,
  Despesa,
  DashboardStats,
  RelatorioMensalItem,
  FluxoCaixa,
  User,
  Cliente,
  Fatura,
  ServicoCatalogo,
  Colaborador,
  Municipio,
  ConfiguracaoLoja,
  AtividadeHistorico,
  SaudeFinanceira,
} from '../models';

@Injectable({
  providedIn: 'root',
})
export class MockStorageService {
  private readonly STORAGE_KEY = 'designart_mock_db_v2';

  private db: {
    tarefas: Tarefa[];
    roteiros: Roteiro[];
    logos: LogoCliente[];
    eventos: Evento[];
    vendas: VendaFoto[];
    despesas: Despesa[];
    clientes: Cliente[];
    faturas: Fatura[];
    servicos: ServicoCatalogo[];
    colaboradores: Colaborador[];
    municipios: Municipio[];
    configLoja: ConfiguracaoLoja;
    historico: AtividadeHistorico[];
  };

  constructor() {
    this.db = this.loadOrSeed();
  }

  private loadOrSeed() {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem(this.STORAGE_KEY);
      if (saved) {
        try {
          return JSON.parse(saved);
        } catch (e) {
          console.warn('Erro ao restaurar DB local, reinicializando com dados padrão');
        }
      }
    }
    const initial = this.getSeedData();
    this.save(initial);
    return initial;
  }

  private save(data = this.db): void {
    if (typeof window !== 'undefined') {
      try {
        localStorage.setItem(this.STORAGE_KEY, JSON.stringify(data));
      } catch (e) {
        console.warn('Falha ao salvar mock DB no localStorage', e);
      }
    }
  }

  // === DASHBOARD STATS ===
  getDashboardStats(): DashboardStats {
    const tarefas = this.db.tarefas;
    const aFazer = tarefas.filter((t) => t.status === 'A_FAZER').length;
    const emDev = tarefas.filter((t) => t.status === 'EM_DESENVOLVIMENTO').length;
    const emRevisao = tarefas.filter((t) => t.status === 'EM_REVISAO' || t.status === 'NAO_HOMOLOGADA').length;
    const atrasadas = tarefas.filter((t) => t.status === 'ATRASADA').length;
    const concluidas = tarefas.filter((t) => t.status === 'CONCLUIDA').length;

    const vendas = this.db.vendas;
    const ganhosNoMes = vendas
      .filter((v) => v.status === 'PAGO')
      .reduce((acc, v) => acc + (v.valorTotal || 0), 0);

    const aReceber = vendas
      .filter((v) => v.status === 'PENDENTE')
      .reduce((acc, v) => acc + (v.valorTotal || 0), 0);

    const atrasados = vendas
      .filter((v) => v.status === 'ATRASADO')
      .reduce((acc, v) => acc + (v.valorTotal || 0), 0);

    return {
      aFazer: aFazer,
      emDesenvolvimento: emDev,
      emRevisaoOuNaoHomologada: emRevisao,
      atrasadas: atrasadas,
      concluidas: concluidas,
      totalTarefas: tarefas.length,
      ganhosNoMes: ganhosNoMes > 0 ? ganhosNoMes : 202.5,
      aReceber: aReceber > 0 ? aReceber : 202.5,
      atrasados: atrasados > 0 ? atrasados : 10.0,
      visitasNaPagina: 324,
      avisos: [
        {
          tarefaId: 3,
          titulo: 'Reformulação de Catálogo Digital',
          loja: 'ÓTICAS PRIME',
          tipo: 'VENCIDA',
          dataEntrega: '2026-08-30',
          prioridade: 'URGENTE',
          status: 'A_FAZER',
          mensagem: 'Demanda atrasada há 2 dias. Exige finalização urgente.',
        },
        {
          tarefaId: 1,
          titulo: 'Produção de conteúdo de marketing',
          loja: 'ATELIÊ DA YSA',
          tipo: 'PROXIMA',
          dataEntrega: '2026-09-18',
          prioridade: 'ALTA',
          status: 'EM_DESENVOLVIMENTO',
          mensagem: 'Entrega prevista para os próximos dias.',
        },
      ],
      producaoMensal: [
        { mes: 'Janeiro', mesAbreviado: 'Jan', atendimentos: 1250, concluidos: 1100 },
        { mes: 'Fevereiro', mesAbreviado: 'Fev', atendimentos: 1420, concluidos: 1300 },
        { mes: 'Março', mesAbreviado: 'Mar', atendimentos: 3650, concluidos: 3400 },
        { mes: 'Abril', mesAbreviado: 'Abr', atendimentos: 3920, concluidos: 3800 },
        { mes: 'Maio', mesAbreviado: 'Mai', atendimentos: 3800, concluidos: 3600 },
        { mes: 'Junho', mesAbreviado: 'Jun', atendimentos: 3710, concluidos: 3500 },
        { mes: 'Julho', mesAbreviado: 'Jul', atendimentos: 3450, concluidos: 3300 },
        { mes: 'Agosto', mesAbreviado: 'Ago', atendimentos: 4200, concluidos: 3950 },
        { mes: 'Setembro', mesAbreviado: 'Set', atendimentos: 2100, concluidos: 1800 },
      ],
      rankingColaboradores: [
        { nome: 'Igor Santos', cargo: 'CEO / Programador', totalTarefasConcluidas: 625 },
        { nome: 'Edyllaine Silva', cargo: 'Videomaker & Editora', totalTarefasConcluidas: 448 },
        { nome: 'Ingrid Ferreira', cargo: 'Social Media & Designer', totalTarefasConcluidas: 364 },
        { nome: 'Lucas Matheus', cargo: 'Roteirista & Criativo', totalTarefasConcluidas: 290 },
        { nome: 'Williane Samia', cargo: 'Fotógrafa & Produção', totalTarefasConcluidas: 214 },
      ],
      ultimasVendas: this.db.vendas.slice(0, 5),
      saudeFinanceira: this.getSaudeFinanceira(),
    };
  }

  // === SAÚDE FINANCEIRA ===
  getSaudeFinanceira(): SaudeFinanceira {
    const folhaSalarial = this.db.colaboradores
      .filter((c) => c.ativo)
      .reduce((acc, c) => acc + (c.salario || 0), 0);

    const custosFixos = this.db.despesas
      .filter((d) => d.status === 'PAGO')
      .reduce((acc, d) => acc + (d.valor || 0), 0);

    const faturasPagas = this.db.faturas
      .filter((f) => f.status === 'PAGO')
      .reduce((acc, f) => acc + (f.valor || 0), 0);

    const faturasEmAberto = this.db.faturas
      .filter((f) => f.status === 'PENDENTE' || f.status === 'ATRASADO')
      .reduce((acc, f) => acc + (f.valor || 0), 0);

    const totalRecebidoMes = faturasPagas + this.db.vendas
      .filter((v) => v.status === 'PAGO')
      .reduce((acc, v) => acc + (v.valorTotal || 0), 0);

    const totalAReceberMes = faturasEmAberto + this.db.vendas
      .filter((v) => v.status === 'PENDENTE' || v.status === 'ATRASADO')
      .reduce((acc, v) => acc + (v.valorTotal || 0), 0);

    const totalDespesas = folhaSalarial + custosFixos;
    const lucroEstimado = totalRecebidoMes - totalDespesas;
    const margemOperacional = totalRecebidoMes > 0 
      ? Number(((lucroEstimado / totalRecebidoMes) * 100).toFixed(1)) 
      : 0.0;

    return {
      margemOperacional,
      folhaSalarial: folhaSalarial || 2900.0,
      custosFixos,
      faturasEmAberto,
      totalRecebidoMes,
      totalAReceberMes,
    };
  }

  // === CLIENTES & MARCAS ===
  getClientes(): Cliente[] {
    return [...this.db.clientes];
  }

  getClienteById(id: number): Cliente | undefined {
    return this.db.clientes.find((c) => c.id === id);
  }

  createCliente(cliente: Partial<Cliente>): Cliente {
    const newId = (this.db.clientes.reduce((max, c) => Math.max(max, c.id || 0), 0) || 0) + 1;
    const novo: Cliente = {
      id: newId,
      nome: cliente.nome || 'Novo Cliente',
      categoria: cliente.categoria || 'Geral',
      segmento: cliente.segmento || cliente.categoria || 'Geral',
      planoContrato: cliente.planoContrato || 'Plano Profissional ++',
      telefone: cliente.telefone || '',
      email: cliente.email || '',
      contato: cliente.contato || cliente.nome || '',
      diaFaturamento: Number(cliente.diaFaturamento) || 17,
      valorMensal: Number(cliente.valorMensal) || 500.0,
      municipio: cliente.municipio || 'São Miguel dos Campos',
      logoUrl: cliente.logoUrl || '',
      status: cliente.status || 'ATIVO',
      dataCadastro: new Date().toISOString(),
    };
    this.db.clientes.unshift(novo);
    
    // Auto-cria primeira fatura do mês
    this.createFatura({
      clienteId: novo.id,
      clienteNome: novo.nome,
      mesReferencia: '09/2026',
      valor: novo.valorMensal,
      dataVencimento: `2026-09-${String(novo.diaFaturamento).padStart(2, '0')}`,
      status: 'PENDENTE',
      observacoes: 'Mensalidade inicial do contrato',
    });

    this.registrarAtividade('CADASTROU CLIENTE', `Cadastrou novo cliente/marca "${novo.nome}"`);
    this.save();
    return novo;
  }

  updateCliente(id: number, cliente: Partial<Cliente>): Cliente {
    const idx = this.db.clientes.findIndex((c) => c.id === id);
    if (idx !== -1) {
      this.db.clientes[idx] = { ...this.db.clientes[idx], ...cliente };
      this.registrarAtividade('EDITOU CLIENTE', `Atualizou informações do cliente "${this.db.clientes[idx].nome}"`);
      this.save();
      return this.db.clientes[idx];
    }
    throw new Error('Cliente não encontrado');
  }

  deleteCliente(id: number): boolean {
    const idx = this.db.clientes.findIndex((c) => c.id === id);
    if (idx !== -1) {
      const nome = this.db.clientes[idx].nome;
      this.db.clientes.splice(idx, 1);
      this.registrarAtividade('EXCLUIU CLIENTE', `Removeu o cliente/marca "${nome}"`);
      this.save();
      return true;
    }
    return false;
  }

  // === FATURAS MENSALIDADE ===
  getFaturas(mesReferencia?: string, status?: string, clienteId?: number): Fatura[] {
    return this.db.faturas.filter((f) => {
      const matchMes = !mesReferencia || f.mesReferencia === mesReferencia;
      const matchStatus = !status || f.status === status;
      const matchCliente = !clienteId || f.clienteId === clienteId;
      return matchMes && matchStatus && matchCliente;
    });
  }

  createFatura(fatura: Partial<Fatura>): Fatura {
    const newId = (this.db.faturas.reduce((max, f) => Math.max(max, f.id || 0), 0) || 0) + 1;
    const nova: Fatura = {
      id: newId,
      clienteId: fatura.clienteId || 1,
      clienteNome: fatura.clienteNome || 'Cliente',
      mesReferencia: fatura.mesReferencia || '09/2026',
      valor: Number(fatura.valor) || 500.0,
      dataVencimento: fatura.dataVencimento || '2026-09-17',
      dataPagamento: fatura.dataPagamento,
      status: fatura.status || 'PENDENTE',
      observacoes: fatura.observacoes || '',
      dataCriacao: new Date().toISOString(),
    };
    this.db.faturas.unshift(nova);
    this.registrarAtividade('LANÇOU FATURA', `Lançou fatura de R$ ${nova.valor.toFixed(2)} para ${nova.clienteNome} (${nova.status})`);
    this.save();
    return nova;
  }

  updateFatura(id: number, fatura: Partial<Fatura>): Fatura {
    const idx = this.db.faturas.findIndex((f) => f.id === id);
    if (idx !== -1) {
      const anterior = this.db.faturas[idx];
      this.db.faturas[idx] = { ...anterior, ...fatura };
      if (fatura.status && fatura.status !== anterior.status) {
        this.registrarAtividade('ALTEROU STATUS DE FATURA', `Alterou status da fatura de ${anterior.clienteNome} para ${fatura.status}`);
      }
      this.save();
      return this.db.faturas[idx];
    }
    throw new Error('Fatura não encontrada');
  }

  toggleStatusFatura(id: number): Fatura {
    const idx = this.db.faturas.findIndex((f) => f.id === id);
    if (idx !== -1) {
      const fatura = this.db.faturas[idx];
      const novoStatus = fatura.status === 'PAGO' ? 'PENDENTE' : 'PAGO';
      fatura.status = novoStatus;
      fatura.dataPagamento = novoStatus === 'PAGO' ? new Date().toISOString().split('T')[0] : undefined;
      this.registrarAtividade('ALTEROU STATUS DE FATURA', `Fatura de ${fatura.clienteNome} marcada como ${novoStatus}`);
      this.save();
      return fatura;
    }
    throw new Error('Fatura não encontrada');
  }

  deleteFatura(id: number): boolean {
    const idx = this.db.faturas.findIndex((f) => f.id === id);
    if (idx !== -1) {
      this.db.faturas.splice(idx, 1);
      this.save();
      return true;
    }
    return false;
  }

  // === SERVIÇOS DO CATÁLOGO ===
  getServicos(): ServicoCatalogo[] {
    return [...this.db.servicos];
  }

  createServico(servico: Partial<ServicoCatalogo>): ServicoCatalogo {
    const newId = (this.db.servicos.reduce((max, s) => Math.max(max, s.id || 0), 0) || 0) + 1;
    const novo: ServicoCatalogo = {
      id: newId,
      nome: servico.nome || 'Novo Plano',
      preco: Number(servico.preco) || 0,
      categoria: servico.categoria || 'Planos',
      descricao: servico.descricao || '',
      status: servico.status || 'ATIVO',
    };
    this.db.servicos.unshift(novo);
    this.registrarAtividade('CADASTROU SERVIÇO', `Criou novo serviço "${novo.nome}" no valor de R$ ${novo.preco.toFixed(2)}`);
    this.save();
    return novo;
  }

  updateServico(id: number, servico: Partial<ServicoCatalogo>): ServicoCatalogo {
    const idx = this.db.servicos.findIndex((s) => s.id === id);
    if (idx !== -1) {
      this.db.servicos[idx] = { ...this.db.servicos[idx], ...servico };
      this.save();
      return this.db.servicos[idx];
    }
    throw new Error('Serviço não encontrado');
  }

  deleteServico(id: number): boolean {
    const idx = this.db.servicos.findIndex((s) => s.id === id);
    if (idx !== -1) {
      this.db.servicos.splice(idx, 1);
      this.save();
      return true;
    }
    return false;
  }

  // === EQUIPE & COLABORADORES ===
  getColaboradores(): Colaborador[] {
    return [...this.db.colaboradores];
  }

  createColaborador(colaborador: Partial<Colaborador>): Colaborador {
    const newId = (this.db.colaboradores.reduce((max, c) => Math.max(max, c.id || 0), 0) || 0) + 1;
    const novo: Colaborador = {
      id: newId,
      nomeCompleto: colaborador.nomeCompleto || 'Colaborador',
      username: colaborador.username || (colaborador.nomeCompleto ? colaborador.nomeCompleto.toLowerCase().replace(/\s+/g, '') : `user${newId}`),
      email: colaborador.email || `${colaborador.username || 'user'}@designarte.com`,
      cargo: colaborador.cargo || 'Operacional',
      salario: Number(colaborador.salario) || 0,
      role: colaborador.role || 'OPERACIONAL',
      ativo: colaborador.ativo !== undefined ? colaborador.ativo : true,
      avatarUrl: colaborador.avatarUrl || '',
    };
    this.db.colaboradores.push(novo);
    this.registrarAtividade('NOVO COLABORADOR', `Cadastrou o colaborador "${novo.nomeCompleto}" (${novo.cargo}) com salário de R$ ${novo.salario.toFixed(2)}`);
    this.save();
    return novo;
  }

  updateColaborador(id: number, colaborador: Partial<Colaborador>): Colaborador {
    const idx = this.db.colaboradores.findIndex((c) => c.id === id);
    if (idx !== -1) {
      this.db.colaboradores[idx] = { ...this.db.colaboradores[idx], ...colaborador };
      this.save();
      return this.db.colaboradores[idx];
    }
    throw new Error('Colaborador não encontrado');
  }

  deleteColaborador(id: number): boolean {
    const idx = this.db.colaboradores.findIndex((c) => c.id === id);
    if (idx !== -1) {
      this.db.colaboradores.splice(idx, 1);
      this.save();
      return true;
    }
    return false;
  }

  // === MUNICÍPIOS ===
  getMunicipios(): Municipio[] {
    return [...this.db.municipios];
  }

  createMunicipio(m: Partial<Municipio>): Municipio {
    const newId = (this.db.municipios.reduce((max, item) => Math.max(max, item.id || 0), 0) || 0) + 1;
    const novo: Municipio = {
      id: newId,
      nome: m.nome || 'Novo Município',
      uf: m.uf || 'AL',
      ativo: m.ativo !== undefined ? m.ativo : true,
    };
    this.db.municipios.push(novo);
    this.registrarAtividade('CADASTROU MUNICÍPIO', `Cadastrou município de atendimento "${novo.nome} - ${novo.uf}"`);
    this.save();
    return novo;
  }

  deleteMunicipio(id: number): boolean {
    const idx = this.db.municipios.findIndex((m) => m.id === id);
    if (idx !== -1) {
      this.db.municipios.splice(idx, 1);
      this.save();
      return true;
    }
    return false;
  }

  // === CONFIGURAÇÃO DA LOJA (RETENÇÃO E DESCONTOS) ===
  getConfigLoja(): ConfiguracaoLoja {
    return { ...this.db.configLoja };
  }

  updateConfigLoja(config: Partial<ConfiguracaoLoja>): ConfiguracaoLoja {
    this.db.configLoja = { ...this.db.configLoja, ...config };
    this.registrarAtividade('CONFIGURAÇÕES LOJA', `Atualizou regras de retenção (${this.db.configLoja.diasRetencao} dias) e descontos`);
    this.save();
    return this.db.configLoja;
  }

  // === HISTÓRICO DE ATIVIDADES ===
  getHistorico(): AtividadeHistorico[] {
    return [...this.db.historico];
  }

  registrarAtividade(acao: string, informacoesAdicionais: string, colaboradorNome = 'Igor Santos', colaboradorEmail = 'igors4ntos8121@gmail.com'): void {
    const now = new Date();
    const formatted = `${String(now.getDate()).padStart(2, '0')}/${String(now.getMonth() + 1).padStart(2, '0')}/${now.getFullYear()}, ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
    const item: AtividadeHistorico = {
      id: Date.now(),
      dataHora: formatted,
      colaboradorNome,
      colaboradorEmail,
      acao,
      informacoesAdicionais,
    };
    this.db.historico.unshift(item);
    if (this.db.historico.length > 100) {
      this.db.historico.pop();
    }
  }

  // === TAREFAS ===
  getTarefas(loja?: string, status?: string, prioridade?: string): Tarefa[] {
    return this.db.tarefas.filter((t) => {
      const matchLoja = !loja || t.loja.toLowerCase().includes(loja.toLowerCase());
      const matchStatus = !status || t.status === status;
      const matchPrioridade = !prioridade || t.prioridade === prioridade;
      return matchLoja && matchStatus && matchPrioridade;
    });
  }

  getTarefaById(id: number): Tarefa | undefined {
    return this.db.tarefas.find((t) => t.id === id);
  }

  getTarefaByToken(token: string): Tarefa | undefined {
    return this.db.tarefas.find((t) => t.tokenAprovacao === token || String(t.id) === token);
  }

  createTarefa(tarefa: Partial<Tarefa>): Tarefa {
    const newId = (this.db.tarefas.reduce((max, t) => Math.max(max, t.id || 0), 0) || 0) + 1;
    const token = `DA-APRV-${Math.random().toString(36).substring(2, 9).toUpperCase()}`;
    const nova: Tarefa = {
      id: newId,
      titulo: tarefa.titulo || 'Nova Demanda',
      descricao: tarefa.descricao || '',
      briefing: tarefa.briefing || '',
      loja: tarefa.loja || 'ATELIÊ DA YSA',
      clienteId: tarefa.clienteId,
      municipio: tarefa.municipio || 'São Miguel dos Campos',
      status: tarefa.status || 'A_FAZER',
      prioridade: tarefa.prioridade || 'MEDIA',
      dataGravacao: tarefa.dataGravacao || '2026-09-13',
      dataEntrega: tarefa.dataEntrega || '2026-09-16',
      criadorNome: tarefa.criadorNome || 'Igor Santos',
      quemAtribuiu: tarefa.quemAtribuiu || 'Igor Santos',
      responsaveis: tarefa.responsaveis && tarefa.responsaveis.length > 0
        ? tarefa.responsaveis
        : ['Igor Santos'],
      checklist: tarefa.checklist && tarefa.checklist.length > 0
        ? tarefa.checklist
        : [
            { id: 1, descricao: 'Planejar conteúdo da semana', concluido: false, ordem: 1 },
            { id: 2, descricao: 'Criar roteiro dos vídeos / Reels', concluido: false, ordem: 2 },
            { id: 3, descricao: 'Gravar vídeos e takes no set', concluido: false, ordem: 3 },
            { id: 4, descricao: 'Editar e finalizar Reels', concluido: false, ordem: 4 },
            { id: 5, descricao: 'Produzir artes para feed e Stories', concluido: false, ordem: 5 },
            { id: 6, descricao: 'Revisar textos e identidade visual', concluido: false, ordem: 6 },
          ],
      arquivosFinais: tarefa.arquivosFinais || [],
      observacoes: tarefa.observacoes || [],
      statusAprovacao: tarefa.statusAprovacao || 'AGUARDANDO_CLIENTE',
      tokenAprovacao: token,
      percentualConcluido: 0,
      dataCriacao: new Date().toISOString(),
    };
    this.recalcularPercentual(nova);
    this.db.tarefas.unshift(nova);
    this.registrarAtividade('CRIOU TAREFA', `Criou a tarefa "${nova.titulo}" atribuída a ${nova.responsaveis.join(', ')}`);
    this.save();
    return nova;
  }

  updateTarefa(id: number, tarefa: Partial<Tarefa>): Tarefa {
    const idx = this.db.tarefas.findIndex((t) => t.id === id);
    if (idx !== -1) {
      const antiga = this.db.tarefas[idx];
      const atualizada: Tarefa = { ...antiga, ...tarefa };
      this.recalcularPercentual(atualizada);
      this.db.tarefas[idx] = atualizada;

      if (tarefa.status && tarefa.status !== antiga.status) {
        this.registrarAtividade('ALTEROU STATUS DA TAREFA', `Alterou status de "${atualizada.titulo}" de "${antiga.status}" para "${tarefa.status}"`);
      }
      this.save();
      return atualizada;
    }
    throw new Error('Tarefa não encontrada');
  }

  deleteTarefa(id: number): boolean {
    const idx = this.db.tarefas.findIndex((t) => t.id === id);
    if (idx !== -1) {
      const titulo = this.db.tarefas[idx].titulo;
      this.db.tarefas.splice(idx, 1);
      this.registrarAtividade('EXCLUIU TAREFA', `Excluiu a tarefa "${titulo}"`);
      this.save();
      return true;
    }
    return false;
  }

  addObservacaoTarefa(tarefaId: number, texto: string, autorNome = 'Igor Santos'): Tarefa {
    const tarefa = this.getTarefaById(tarefaId);
    if (tarefa) {
      if (!tarefa.observacoes) tarefa.observacoes = [];
      const now = new Date();
      const formatted = `${String(now.getDate()).padStart(2, '0')}/${String(now.getMonth() + 1).padStart(2, '0')}/${now.getFullYear()} às ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
      tarefa.observacoes.push({
        id: Date.now(),
        autorNome,
        dataHora: formatted,
        texto,
      });
      this.registrarAtividade('COMENTOU NA TAREFA', `Adicionou um comentário na tarefa "${tarefa.titulo}"`);
      this.save();
      return tarefa;
    }
    throw new Error('Tarefa não encontrada');
  }

  addArquivoFinal(tarefaId: number, arquivo: { nome: string; urlOuBase64: string; tipo?: 'IMAGEM' | 'VIDEO' | 'DOCUMENTO' }): Tarefa {
    const tarefa = this.getTarefaById(tarefaId);
    if (tarefa) {
      if (!tarefa.arquivosFinais) tarefa.arquivosFinais = [];
      tarefa.arquivosFinais.push({
        id: Date.now(),
        nome: arquivo.nome,
        urlOuBase64: arquivo.urlOuBase64,
        tipo: arquivo.tipo || 'IMAGEM',
        dataUpload: new Date().toISOString(),
      });
      this.registrarAtividade('ANEXOU ARQUIVO', `Anexou arquivo final "${arquivo.nome}" na tarefa "${tarefa.titulo}"`);
      this.save();
      return tarefa;
    }
    throw new Error('Tarefa não encontrada');
  }

  removeArquivoFinal(tarefaId: number, arquivoId: number): Tarefa {
    const tarefa = this.getTarefaById(tarefaId);
    if (tarefa && tarefa.arquivosFinais) {
      tarefa.arquivosFinais = tarefa.arquivosFinais.filter((a) => a.id !== arquivoId);
      this.save();
      return tarefa;
    }
    throw new Error('Tarefa ou arquivo não encontrado');
  }

  responderAprovacao(tarefaId: number, statusAprovacao: 'APROVADO' | 'SOLICITOU_AJUSTE', feedback?: string): Tarefa {
    const tarefa = this.getTarefaById(tarefaId);
    if (tarefa) {
      tarefa.statusAprovacao = statusAprovacao;
      if (statusAprovacao === 'APROVADO') {
        tarefa.status = 'CONCLUIDA';
        tarefa.dataConclusao = new Date().toISOString().split('T')[0];
        this.registrarAtividade('APROVAÇÃO CLIENTE', `Cliente APROVOU o projeto "${tarefa.titulo}"`);
      } else {
        tarefa.status = 'EM_REVISAO';
        if (feedback) {
          this.addObservacaoTarefa(tarefaId, `[CLIENTE SOLICITOU AJUSTES]: ${feedback}`, 'Cliente');
        }
        this.registrarAtividade('APROVAÇÃO CLIENTE', `Cliente SOLICITOU AJUSTES no projeto "${tarefa.titulo}"`);
      }
      this.save();
      return tarefa;
    }
    throw new Error('Tarefa não encontrada');
  }

  private recalcularPercentual(tarefa: Tarefa): void {
    if (!tarefa.checklist || tarefa.checklist.length === 0) {
      tarefa.percentualConcluido = tarefa.status === 'CONCLUIDA' ? 100 : 0;
      return;
    }
    const total = tarefa.checklist.length;
    const concluidos = tarefa.checklist.filter((i) => i.concluido).length;
    tarefa.percentualConcluido = Math.round((concluidos / total) * 100);
    if (tarefa.percentualConcluido === 100 && tarefa.status !== 'CONCLUIDA') {
      tarefa.status = 'CONCLUIDA';
      tarefa.dataConclusao = new Date().toISOString().split('T')[0];
    }
  }

  // === ROTEIROS ===
  getRoteiros(loja?: string, status?: string): Roteiro[] {
    return this.db.roteiros.filter((r) => {
      const matchLoja = !loja || r.loja.toLowerCase().includes(loja.toLowerCase());
      const matchStatus = !status || r.status === status;
      return matchLoja && matchStatus;
    });
  }

  getRoteiroById(id: number): Roteiro | undefined {
    return this.db.roteiros.find((r) => r.id === id);
  }

  createRoteiro(roteiro: Partial<Roteiro>): Roteiro {
    const newId = (this.db.roteiros.reduce((max, r) => Math.max(max, r.id || 0), 0) || 0) + 1;
    const novo: Roteiro = {
      id: newId,
      titulo: roteiro.titulo || 'Novo Roteiro',
      loja: roteiro.loja || 'ATELIÊ DA YSA',
      clienteNome: roteiro.clienteNome || roteiro.loja || 'ATELIÊ DA YSA',
      tarefaId: roteiro.tarefaId,
      tarefaTitulo: roteiro.tarefaTitulo,
      criadorNome: roteiro.criadorNome || 'Lucas Matheus',
      dataGravacao: roteiro.dataGravacao || '2026-09-20',
      conteudoScript: roteiro.conteudoScript || '',
      cenas: roteiro.cenas || [
        { ordem: 1, descricao: 'CENA 1: Modelo entrando no ateliê em slow-motion admirando os vestidos.' },
        { ordem: 2, descricao: 'CENA 2: Close-up nos tecidos bordados à mão e detalhes da costura autêntica.' },
        { ordem: 3, descricao: 'CENA 3: Ysa explicando a inspiração botânica da nova coleção.' }
      ],
      instrucoesCamera: roteiro.instrucoesCamera || 'Ex: Lente 50mm, luz suave quente...',
      observacoesSet: roteiro.observacoesSet || '',
      status: roteiro.status || 'PENDENTE',
      feito: roteiro.feito || false,
      dataCriacao: new Date().toISOString(),
    };
    this.db.roteiros.unshift(novo);
    this.registrarAtividade('CRIOU ROTEIRO', `Criou o roteiro "${novo.titulo}" para ${novo.loja}`);
    this.save();
    return novo;
  }

  updateRoteiro(id: number, roteiro: Partial<Roteiro>): Roteiro {
    const idx = this.db.roteiros.findIndex((r) => r.id === id);
    if (idx !== -1) {
      const antigo = this.db.roteiros[idx];
      this.db.roteiros[idx] = { ...antigo, ...roteiro };
      if (roteiro.status === 'CONCLUIDO' || (roteiro.feito && !antigo.feito)) {
        this.registrarAtividade('CONCLUIU ROTEIRO', `Marcou roteiro "${this.db.roteiros[idx].titulo}" como Gravação Concluída`);
      }
      this.save();
      return this.db.roteiros[idx];
    }
    throw new Error('Roteiro não encontrado');
  }

  toggleGravacaoRoteiro(id: number): Roteiro {
    const idx = this.db.roteiros.findIndex((r) => r.id === id);
    if (idx !== -1) {
      const r = this.db.roteiros[idx];
      r.feito = !r.feito;
      r.status = r.feito ? 'CONCLUIDO' : 'PENDENTE';
      this.registrarAtividade('STATUS ROTEIRO', `Roteiro "${r.titulo}" alternado para ${r.status}`);
      this.save();
      return r;
    }
    throw new Error('Roteiro não encontrado');
  }

  deleteRoteiro(id: number): boolean {
    const idx = this.db.roteiros.findIndex((r) => r.id === id);
    if (idx !== -1) {
      this.db.roteiros.splice(idx, 1);
      this.save();
      return true;
    }
    return false;
  }

  // === LOGOS ===
  getLogos(cliente?: string): LogoCliente[] {
    if (!cliente || cliente.trim() === '') {
      return [...this.db.logos];
    }
    return this.db.logos.filter((l) => l.clienteNome.toLowerCase().includes(cliente.toLowerCase()));
  }

  createLogo(logo: Partial<LogoCliente>): LogoCliente {
    const newId = (this.db.logos.reduce((max, l) => Math.max(max, l.id || 0), 0) || 0) + 1;
    const nova: LogoCliente = {
      id: newId,
      clienteNome: logo.clienteNome || 'Cliente',
      variante: logo.variante || 'Principal',
      formato: logo.formato || 'PNG',
      arquivoUrlOuBase64: logo.arquivoUrlOuBase64 || '/logo-da.png',
      tamanho: logo.tamanho || '1.2 MB',
      corPrimaria: logo.corPrimaria || '#7c3aed',
      dataUpload: new Date().toISOString(),
    };
    this.db.logos.unshift(nova);
    this.save();
    return nova;
  }

  deleteLogo(id: number): boolean {
    const idx = this.db.logos.findIndex((l) => l.id === id);
    if (idx !== -1) {
      this.db.logos.splice(idx, 1);
      this.save();
      return true;
    }
    return false;
  }

  // === EVENTOS & LOJA DE FOTOS ===
  getEventos(): Evento[] {
    return [...this.db.eventos];
  }

  getEventoById(id: number): Evento | undefined {
    return this.db.eventos.find((e) => e.id === id);
  }

  createEvento(evento: Partial<Evento>): Evento {
    const newId = (this.db.eventos.reduce((max, e) => Math.max(max, e.id || 0), 0) || 0) + 1;
    const slug = (evento.nome || 'evento').toLowerCase().replace(/[^a-z0-9]+/g, '-');
    const novo: Evento = {
      id: newId,
      nome: evento.nome || 'Novo Evento',
      slug,
      localizacao: evento.localizacao || 'Barra de São Miguel - AL, Brasil',
      dataEvento: evento.dataEvento || '2026-10-01',
      horario: evento.horario || '06:00 às 11:00',
      precoFotoVendida: Number(evento.precoFotoVendida) || 10.0,
      publicoEstimado: Number(evento.publicoEstimado) || 500,
      bannerUrl: evento.bannerUrl || 'https://images.unsplash.com/photo-1530549387789-4c1017266635?auto=format&fit=crop&w=1200&q=80',
      descricao: evento.descricao || '',
      status: 'PUBLICADO',
      fotos: evento.fotos || [],
    };
    this.db.eventos.unshift(novo);
    this.registrarAtividade('CRIOU ÁLBUM/EVENTO', `Publicou novo evento "${novo.nome}" na Loja de Fotos`);
    this.save();
    return novo;
  }

  addFotoEvento(eventoId: number, foto: Partial<FotoEvento>): Evento {
    const evento = this.getEventoById(eventoId);
    if (evento) {
      if (!evento.fotos) evento.fotos = [];
      const newFotoId = (evento.fotos.reduce((max, f) => Math.max(max, f.id || 0), 0) || 0) + 1;
      const novaFoto: FotoEvento = {
        id: newFotoId,
        codigoFoto: foto.codigoFoto || `FOTO-${String(newFotoId).padStart(4, '0')}`,
        titulo: foto.titulo || `Foto #${newFotoId}`,
        urlOuBase64: foto.urlOuBase64 || 'https://images.unsplash.com/photo-1517838277536-f5f99be501cd?auto=format&fit=crop&w=800&q=80',
        preco: Number(foto.preco) || evento.precoFotoVendida || 10.0,
        marcaDaguaTexto: foto.marcaDaguaTexto || 'DESIGN ART - AMOSTRA',
        visualizacoes: 0,
        vendas: 0,
      };
      evento.fotos.push(novaFoto);
      this.registrarAtividade('ADICIONOU FOTO', `Adicionou foto "${novaFoto.titulo}" ao evento "${evento.nome}"`);
      this.save();
      return evento;
    }
    throw new Error('Evento não encontrado');
  }

  deleteFotoEvento(fotoId: number): boolean {
    for (const evento of this.db.eventos) {
      if (evento.fotos) {
        const idx = evento.fotos.findIndex((f) => f.id === fotoId);
        if (idx !== -1) {
          evento.fotos.splice(idx, 1);
          this.registrarAtividade('EXCLUIU FOTO', `Excluiu foto do evento "${evento.nome}"`);
          this.save();
          return true;
        }
      }
    }
    return false;
  }

  deleteEvento(id: number): boolean {
    const idx = this.db.eventos.findIndex((e) => e.id === id);
    if (idx !== -1) {
      this.db.eventos.splice(idx, 1);
      this.save();
      return true;
    }
    return false;
  }

  // === VENDAS DE FOTOS ===
  getVendas(status?: string): VendaFoto[] {
    return this.db.vendas.filter((v) => !status || v.status === status);
  }

  createVenda(venda: Partial<VendaFoto>): VendaFoto {
    const newId = (this.db.vendas.reduce((max, v) => Math.max(max, v.id || 0), 0) || 0) + 1;
    const nova: VendaFoto = {
      id: newId,
      codigoVenda: `#${Math.floor(10000000 + Math.random() * 90000000)}`,
      clienteNome: venda.clienteNome || 'Cliente Anônimo',
      clienteEmail: venda.clienteEmail,
      eventoNome: venda.eventoNome || 'Barra Run 2026',
      qtdFotos: venda.qtdFotos || 1,
      qtdVideos: venda.qtdVideos || 0,
      valorTotal: venda.valorTotal || 10.0,
      status: venda.status || 'PAGO',
      dataDisponivelInfo: 'Disponível em 20/07/2026',
      dataVenda: new Date().toISOString(),
    };
    this.db.vendas.unshift(nova);
    this.save();
    return nova;
  }

  // === DESPESAS OPERACIONAIS ===
  getDespesas(): Despesa[] {
    return [...this.db.despesas];
  }

  createDespesa(despesa: Partial<Despesa>): Despesa {
    const newId = (this.db.despesas.reduce((max, d) => Math.max(max, d.id || 0), 0) || 0) + 1;
    const nova: Despesa = {
      id: newId,
      descricao: despesa.descricao || 'Despesa Operacional',
      categoria: despesa.categoria || 'Equipamentos',
      valor: Number(despesa.valor) || 0,
      dataDespesa: despesa.dataDespesa || new Date().toISOString().split('T')[0],
      formaPagamento: despesa.formaPagamento || 'PIX',
      status: despesa.status || 'PAGO',
      observacoes: despesa.observacoes || '',
    };
    this.db.despesas.unshift(nova);
    this.registrarAtividade('LANÇOU DESPESA', `Cadastrou despesa "${nova.descricao}" de R$ ${nova.valor.toFixed(2)}`);
    this.save();
    return nova;
  }

  deleteDespesa(id: number): boolean {
    const idx = this.db.despesas.findIndex((d) => d.id === id);
    if (idx !== -1) {
      this.db.despesas.splice(idx, 1);
      this.save();
      return true;
    }
    return false;
  }

  // === RELATÓRIOS MENSAIS ===
  getRelatorioMensal(loja?: string, mes?: any, ano?: any): RelatorioMensalItem[] {
    return this.db.tarefas
      .filter((t) => {
        const matchLoja = !loja || loja === 'TODAS' || t.loja.toLowerCase().includes(loja.toLowerCase());
        return matchLoja;
      })
      .map((t) => ({
        tarefaId: t.id || 0,
        tituloDemanda: t.titulo,
        loja: t.loja,
        criadorNome: t.criadorNome || 'Lucas Matheus',
        participantes: t.responsaveis || [],
        status: t.status,
        prioridade: t.prioridade,
        dataEntrega: t.dataEntrega || '2026-09-16',
        percentualConcluido: t.percentualConcluido || 0,
        mesAno: typeof mes === 'string' && mes.includes('/') ? mes : (mes ? `${String(mes).padStart(2, '0')}/${ano || 2026}` : '09/2026'),
      }));
  }

  getFluxoCaixa(): FluxoCaixa {
    const faturasRecebidas = this.db.faturas
      .filter((f) => f.status === 'PAGO')
      .reduce((acc, f) => acc + (f.valor || 0), 0);
    const vendasRecebidas = this.db.vendas
      .filter((v) => v.status === 'PAGO')
      .reduce((acc, v) => acc + (v.valorTotal || 0), 0);
    const totalEntradas = faturasRecebidas + vendasRecebidas;

    const folha = this.db.colaboradores
      .filter((c) => c.ativo)
      .reduce((acc, c) => acc + (c.salario || 0), 0);
    const despesas = this.db.despesas
      .filter((d) => d.status === 'PAGO')
      .reduce((acc, d) => acc + (d.valor || 0), 0);
    const totalSaidas = folha + despesas;

    return {
      totalEntradas,
      totalSaidas,
      lucroLiquido: totalEntradas - totalSaidas,
      comparativosMensais: [
        { mes: 'Fevereiro', mesAbreviado: 'FEV', entradas: 4200, saidas: 3100, lucro: 1100 },
        { mes: 'Março', mesAbreviado: 'MAR', entradas: 5800, saidas: 3400, lucro: 2400 },
        { mes: 'Abril', mesAbreviado: 'ABR', entradas: 6500, saidas: 3900, lucro: 2600 },
        { mes: 'Maio', mesAbreviado: 'MAI', entradas: 7200, saidas: 4100, lucro: 3100 },
        { mes: 'Junho', mesAbreviado: 'JUN', entradas: 8100, saidas: 4300, lucro: 3800 },
        { mes: 'Julho', mesAbreviado: 'JUL', entradas: 8900, saidas: 4600, lucro: 4300 },
        { mes: 'Agosto', mesAbreviado: 'AGO', entradas: 9400, saidas: 4800, lucro: 4600 },
        { mes: 'Setembro', mesAbreviado: 'SET', entradas: totalEntradas, saidas: totalSaidas, lucro: totalEntradas - totalSaidas },
      ],
      ultimasDespesas: this.db.despesas.slice(0, 8),
    };
  }

  // === SEED DATA INICIAL ===
  private getSeedData() {
    return {
      clientes: [
        {
          id: 1,
          nome: 'ACADEMIA TITANIUM',
          categoria: 'ACADEMIA',
          segmento: 'ACADEMIA',
          planoContrato: 'Plano Profissional ++',
          telefone: '82 9985-3023',
          email: 'contato@titaniumfitness.com',
          contato: 'Rodrigo Medeiros',
          diaFaturamento: 17,
          valorMensal: 500.0,
          municipio: 'São Miguel dos Campos',
          logoUrl: 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=150&q=80',
          status: 'ATIVO' as const,
          dataCadastro: '2026-01-15T10:00:00Z',
        },
        {
          id: 2,
          nome: 'MERCADO NOVO SÃO JOÃO',
          categoria: 'SUPERMERCADO',
          segmento: 'SUPERMERCADO',
          planoContrato: 'Plano Creator Plus - Mercados de Rede',
          telefone: '82 9912-4455',
          email: 'marketing@novosaojoao.com',
          contato: 'Carlos Eduardo',
          diaFaturamento: 10,
          valorMensal: 600.0,
          municipio: 'São Miguel dos Campos',
          logoUrl: 'https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=150&q=80',
          status: 'ATIVO' as const,
          dataCadastro: '2026-02-10T11:00:00Z',
        },
        {
          id: 3,
          nome: 'CLOSET KLIVIA FREITAS',
          categoria: 'MODA & VAREJO',
          segmento: 'MODA & VAREJO',
          planoContrato: 'Plano Profissional',
          telefone: '82 9884-1122',
          email: 'klivia@closetklivia.com',
          contato: 'Klivia Freitas',
          diaFaturamento: 5,
          valorMensal: 400.0,
          municipio: 'Maceió',
          logoUrl: 'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=150&q=80',
          status: 'ATIVO' as const,
          dataCadastro: '2026-03-01T09:30:00Z',
        },
        {
          id: 4,
          nome: 'ATELIÊ DA YSA',
          categoria: 'ALTA COSTURA',
          segmento: 'ALTA COSTURA',
          planoContrato: 'Plano Profissional ++',
          telefone: '82 9976-5544',
          email: 'ysa@ateliedaysa.com',
          contato: 'Thaysa Wanderley',
          diaFaturamento: 20,
          valorMensal: 500.0,
          municipio: 'Maceió',
          logoUrl: 'https://images.unsplash.com/photo-1558769132-cb1aea458c5e?auto=format&fit=crop&w=150&q=80',
          status: 'ATIVO' as const,
          dataCadastro: '2026-03-12T14:20:00Z',
        },
        {
          id: 5,
          nome: 'O SUPERMERCADO FAVORITO',
          categoria: 'SUPERMERCADO',
          segmento: 'SUPERMERCADO',
          planoContrato: 'Plano Creator Plus - Mercados de Rede',
          telefone: '82 9933-7788',
          email: 'compras@favorito.com',
          contato: 'Marcio Silva',
          diaFaturamento: 15,
          valorMensal: 600.0,
          municipio: 'Marechal Deodoro',
          logoUrl: 'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=150&q=80',
          status: 'ATIVO' as const,
          dataCadastro: '2026-04-05T08:00:00Z',
        },
        {
          id: 6,
          nome: 'SR. JUNIOR',
          categoria: 'BARBEARIA & ESTÉTICA',
          segmento: 'BARBEARIA & ESTÉTICA',
          planoContrato: 'Plano Essencial',
          telefone: '82 9955-6677',
          email: 'junior@srjunior.com',
          contato: 'Alexandro Junior',
          diaFaturamento: 25,
          valorMensal: 300.0,
          municipio: 'São Miguel dos Campos',
          logoUrl: 'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?auto=format&fit=crop&w=150&q=80',
          status: 'ATIVO' as const,
          dataCadastro: '2026-04-18T16:00:00Z',
        },
        {
          id: 7,
          nome: 'VIVAMAIS MERCADO NATURAL',
          categoria: 'SAÚDE & SUPLEMENTOS',
          segmento: 'SAÚDE & SUPLEMENTOS',
          planoContrato: 'Plano Profissional',
          telefone: '82 9911-3322',
          email: 'contato@vivamais.com',
          contato: 'Leo e Adrielle',
          diaFaturamento: 12,
          valorMensal: 400.0,
          municipio: 'Arapiraca',
          logoUrl: 'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=150&q=80',
          status: 'ATIVO' as const,
          dataCadastro: '2026-05-02T10:30:00Z',
        },
      ],
      faturas: [
        {
          id: 1,
          clienteId: 1,
          clienteNome: 'ACADEMIA TITANIUM',
          mesReferencia: '09/2026',
          valor: 500.0,
          dataVencimento: '2026-09-17',
          dataPagamento: undefined,
          status: 'PENDENTE' as const,
          observacoes: 'Mensalidade Setembro - Contrato Profissional ++',
          dataCriacao: '2026-09-01T08:00:00Z',
        },
        {
          id: 2,
          clienteId: 2,
          clienteNome: 'MERCADO NOVO SÃO JOÃO',
          mesReferencia: '09/2026',
          valor: 600.0,
          dataVencimento: '2026-09-10',
          dataPagamento: '2026-09-10',
          status: 'PAGO' as const,
          observacoes: 'Pago via PIX no vencimento',
          dataCriacao: '2026-09-01T08:00:00Z',
        },
        {
          id: 3,
          clienteId: 3,
          clienteNome: 'CLOSET KLIVIA FREITAS',
          mesReferencia: '09/2026',
          valor: 400.0,
          dataVencimento: '2026-09-05',
          dataPagamento: '2026-09-05',
          status: 'PAGO' as const,
          observacoes: 'Transferência bancária confirmada',
          dataCriacao: '2026-09-01T08:00:00Z',
        },
        {
          id: 4,
          clienteId: 4,
          clienteNome: 'ATELIÊ DA YSA',
          mesReferencia: '09/2026',
          valor: 500.0,
          dataVencimento: '2026-09-20',
          dataPagamento: undefined,
          status: 'PENDENTE' as const,
          observacoes: 'Fatura a vencer no dia 20',
          dataCriacao: '2026-09-01T08:00:00Z',
        },
        {
          id: 5,
          clienteId: 5,
          clienteNome: 'O SUPERMERCADO FAVORITO',
          mesReferencia: '09/2026',
          valor: 600.0,
          dataVencimento: '2026-09-15',
          dataPagamento: undefined,
          status: 'PENDENTE' as const,
          observacoes: 'Boleto enviado para financeiro',
          dataCriacao: '2026-09-01T08:00:00Z',
        },
        {
          id: 6,
          clienteId: 6,
          clienteNome: 'SR. JUNIOR',
          mesReferencia: '09/2026',
          valor: 300.0,
          dataVencimento: '2026-09-25',
          dataPagamento: undefined,
          status: 'PENDENTE' as const,
          observacoes: 'Mensalidade Setembro',
          dataCriacao: '2026-09-01T08:00:00Z',
        },
        {
          id: 7,
          clienteId: 7,
          clienteNome: 'VIVAMAIS MERCADO NATURAL',
          mesReferencia: '09/2026',
          valor: 400.0,
          dataVencimento: '2026-09-12',
          dataPagamento: undefined,
          status: 'PENDENTE' as const,
          observacoes: 'Aguardando confirmação bancária',
          dataCriacao: '2026-09-01T08:00:00Z',
        },
      ],
      servicos: [
        {
          id: 1,
          nome: 'Plano Place – Vereadores',
          preco: 600.0,
          categoria: 'Político & Institucional',
          descricao: 'O plano inclui: Cobertura completa das sessões da Câmara Municipal; Acompanhamento de visitas, fiscalizações e ações em campo; Cobertura de eventos...',
          status: 'ATIVO' as const,
        },
        {
          id: 2,
          nome: 'Plano Creator Plus – Mercados de Rede',
          preco: 600.0,
          categoria: 'Varejo & Redes',
          descricao: 'O Plano Creator Plus foi desenvolvido para influenciadores digitais, criadores de conteúdo e embaixadores de marcas que desejam manter uma presença marcante...',
          status: 'ATIVO' as const,
        },
        {
          id: 3,
          nome: 'Plano Ecta Place – Cobertura Institucional',
          preco: 4500.0,
          categoria: 'Prefeituras & Órgãos',
          descricao: 'O Plano Ecta Place foi desenvolvido para atender prefeituras, secretarias, institutos, autarquias e demais órgãos públicos que desejam registrar e divulgar...',
          status: 'ATIVO' as const,
        },
        {
          id: 4,
          nome: 'Plano Essencial',
          preco: 300.0,
          categoria: 'Comércio Local',
          descricao: 'Planejamento, criação e publicação mensal para redes sociais com peças profissionais e design alinhado.',
          status: 'ATIVO' as const,
        },
        {
          id: 5,
          nome: 'Plano Profissional',
          preco: 400.0,
          categoria: 'Empresarial',
          descricao: 'Produção de vídeos para redes sociais; Criação de roteiros personalizados para cada conteúdo; Sessão com fotógrafo e videomaker para produção de imagens...',
          status: 'ATIVO' as const,
        },
        {
          id: 6,
          nome: 'Plano Profissional ++',
          preco: 500.0,
          categoria: 'Empresarial Premium',
          descricao: 'Produção de vídeos para redes sociais; Criação de roteiros personalizados para cada conteúdo; Modelos prontos para publicações e estratégias de comunicação...',
          status: 'ATIVO' as const,
        },
        {
          id: 7,
          nome: 'Plano Secs Municipais',
          preco: 1400.0,
          categoria: 'Secretarias',
          descricao: 'Acompanhamento integral das ações das secretarias municipais com equipe de captação e edição rápida para divulgação pública.',
          status: 'ATIVO' as const,
        },
        {
          id: 8,
          nome: 'Plano Select – Produção de Video',
          preco: 250.0,
          categoria: 'Audiovisual',
          descricao: 'Roteiro, captação e edição de vídeo em alta resolução.',
          status: 'ATIVO' as const,
        },
        {
          id: 9,
          nome: 'Sessão de Fotos – Aniversário',
          preco: 850.0,
          categoria: 'Eventos & Ensaios',
          descricao: 'O plano inclui: 📷 Cobertura fotográfica de todos os momentos da festa; 🎥 Captação de vídeos dos melhores momentos; 🚁 Imagens aéreas com drone...',
          status: 'ATIVO' as const,
        },
      ],
      colaboradores: [
        {
          id: 1,
          nomeCompleto: 'Igor Santos',
          username: 'igorsantos',
          email: 'igors4ntos8121@gmail.com',
          cargo: 'CEO / Programador',
          salario: 1000.0,
          role: 'ADMIN' as const,
          ativo: true,
          avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80',
        },
        {
          id: 2,
          nomeCompleto: 'Edyllaine Silva',
          username: 'edyllaine',
          email: 'edyllaine@designarte.com',
          cargo: 'Videomaker & Editora',
          salario: 700.0,
          role: 'OPERACIONAL' as const,
          ativo: true,
          avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80',
        },
        {
          id: 3,
          nomeCompleto: 'Ingrid Ferreira',
          username: 'ingrid',
          email: 'ingrid@designarte.com',
          cargo: 'Social Media & Designer',
          salario: 500.0,
          role: 'OPERACIONAL' as const,
          ativo: true,
          avatarUrl: 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=150&q=80',
        },
        {
          id: 4,
          nomeCompleto: 'Lucas Matheus',
          username: 'lucas',
          email: 'lucas@designarte.com',
          cargo: 'Roteirista & Criativo',
          salario: 400.0,
          role: 'OPERACIONAL' as const,
          ativo: true,
          avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80',
        },
        {
          id: 5,
          nomeCompleto: 'Williane Samia',
          username: 'williane',
          email: 'williane@designarte.com',
          cargo: 'Fotógrafa & Produção',
          salario: 300.0,
          role: 'FOTOGRAFO' as const,
          ativo: true,
          avatarUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=150&q=80',
        },
      ],
      municipios: [
        { id: 1, nome: 'São Miguel dos Campos', uf: 'AL', ativo: true },
        { id: 2, nome: 'Maceió', uf: 'AL', ativo: true },
        { id: 3, nome: 'Marechal Deodoro', uf: 'AL', ativo: true },
        { id: 4, nome: 'Arapiraca', uf: 'AL', ativo: true },
        { id: 5, nome: 'Barra de São Miguel', uf: 'AL', ativo: true },
        { id: 6, nome: 'Pilar', uf: 'AL', ativo: true },
        { id: 7, nome: 'Coruripe', uf: 'AL', ativo: true },
      ],
      configLoja: {
        diasRetencao: 10,
        faixasDesconto: [
          { id: 1, qtdMinima: 5, percentualDesconto: 10 },
          { id: 2, qtdMinima: 10, percentualDesconto: 20 },
          { id: 3, qtdMinima: 20, percentualDesconto: 30 },
        ],
      },
      historico: [
        {
          id: 1,
          dataHora: '02/09/2026, 01:30:32',
          colaboradorNome: 'Igor Santos',
          colaboradorEmail: 'igors4ntos8121@gmail.com',
          acao: 'CRIOU TAREFA',
          informacoesAdicionais: 'Criou a tarefa "Saúde no campo" atribuída a 2 pessoas',
        },
        {
          id: 2,
          dataHora: '31/08/2026, 23:25:57',
          colaboradorNome: 'Igor Santos',
          colaboradorEmail: 'igors4ntos8121@gmail.com',
          acao: 'ALTEROU STATUS DA TAREFA',
          informacoesAdicionais: 'Alterou status de "Promoção" de "Concluido" para "Concluido"',
        },
        {
          id: 3,
          dataHora: '31/08/2026, 23:25:56',
          colaboradorNome: 'Igor Santos',
          colaboradorEmail: 'igors4ntos8121@gmail.com',
          acao: 'ALTEROU STATUS DA TAREFA',
          informacoesAdicionais: 'Alterou status de "Promoção" de "Em andamento" para "Concluido"',
        },
        {
          id: 4,
          dataHora: '31/08/2026, 23:25:29',
          colaboradorNome: 'Igor Santos',
          colaboradorEmail: 'igors4ntos8121@gmail.com',
          acao: 'ALTEROU STATUS DA TAREFA',
          informacoesAdicionais: 'Alterou status de "Promoção" de "A Fazer" para "Em andamento"',
        },
        {
          id: 5,
          dataHora: '31/08/2026, 23:25:08',
          colaboradorNome: 'Igor Santos',
          colaboradorEmail: 'igors4ntos8121@gmail.com',
          acao: 'CRIOU TAREFA',
          informacoesAdicionais: 'Criou a tarefa "Promoção" atribuída a 1 pessoas',
        },
        {
          id: 6,
          dataHora: '31/08/2026, 15:48:50',
          colaboradorNome: 'Igor Santos',
          colaboradorEmail: 'igors4ntos8121@gmail.com',
          acao: 'COMENTOU NA TAREFA',
          informacoesAdicionais: 'Adicionou um comentário na tarefa "Programação festas cidades vizinhas"',
        },
        {
          id: 7,
          dataHora: '31/08/2026, 15:47:28',
          colaboradorNome: 'Igor Santos',
          colaboradorEmail: 'igors4ntos8121@gmail.com',
          acao: 'CRIOU TAREFA',
          informacoesAdicionais: 'Criou a tarefa "Programação festas cidades vizinhas" atribuída a 4 pessoas',
        },
        {
          id: 8,
          dataHora: '29/08/2026, 08:43:28',
          colaboradorNome: 'Igor Santos',
          colaboradorEmail: 'igors4ntos8121@gmail.com',
          acao: 'ALTEROU STATUS DA TAREFA',
          informacoesAdicionais: 'Alterou status de "Promo" de "Concluido" para "Concluido"',
        },
      ],
      tarefas: [
        {
          id: 1,
          titulo: 'Promoção',
          descricao: 'Campanha de ofertas imbatíveis de fim de semana com encarte digital e stories animados.',
          briefing: 'Enfatizar ofertas do setor de laticínios (Margarina Deline 3kg) e cortes especiais de carnes.',
          loja: 'Mercado novo São João',
          clienteId: 2,
          municipio: 'São Miguel dos Campos',
          status: 'CONCLUIDA' as const,
          prioridade: 'ALTA' as const,
          dataGravacao: '2026-08-30',
          dataEntrega: '2026-08-31',
          criadorNome: 'Igor Santos',
          quemAtribuiu: 'Igor Santos',
          responsaveis: ['Igor Santos'],
          checklist: [
            { id: 1, descricao: 'Definir produtos e preços com o gerente', concluido: true, ordem: 1 },
            { id: 2, descricao: 'Fotografar produtos em destaque', concluido: true, ordem: 2 },
            { id: 3, descricao: 'Diagramar artes no formato Feed e Story', concluido: true, ordem: 3 },
            { id: 4, descricao: 'Exportar arquivos em alta resolução', concluido: true, ordem: 4 },
          ],
          arquivosFinais: [
            { id: 101, nome: '1.png', urlOuBase64: 'https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
            { id: 102, nome: '2.png', urlOuBase64: 'https://images.unsplash.com/photo-1588964895597-cfccd6e2dbf9?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
            { id: 103, nome: '3.png', urlOuBase64: 'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
            { id: 104, nome: '4.png', urlOuBase64: 'https://images.unsplash.com/photo-1506617564039-2f3b650b7010?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
            { id: 105, nome: '5.png', urlOuBase64: 'https://images.unsplash.com/photo-1534723452862-4c874018d66d?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
            { id: 106, nome: '6.png', urlOuBase64: 'https://images.unsplash.com/photo-1583258292688-d0213dc5a3a8?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
            { id: 107, nome: '7.png', urlOuBase64: 'https://images.unsplash.com/photo-1526367790999-0150786686a2?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
            { id: 108, nome: '8.png', urlOuBase64: 'https://images.unsplash.com/photo-1543163521-1bf539c55dd2?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
          ],
          observacoes: [
            { id: 1, autorNome: 'Igor Santos', dataHora: '31/08/2026 às 23:20', texto: 'Todas as 8 artes finalizadas e prontas para aprovação do cliente.' },
          ],
          statusAprovacao: 'AGUARDANDO_CLIENTE' as const,
          tokenAprovacao: 'DA-APRV-SJOAO01',
          percentualConcluido: 100,
          dataCriacao: '2026-08-31T08:00:00Z',
          dataConclusao: '2026-08-31',
        },
        {
          id: 2,
          titulo: 'Promo',
          descricao: 'Artes de ofertas da semana.',
          briefing: 'Produzir 9 artes promocionais.',
          loja: 'Mercado novo São João',
          clienteId: 2,
          municipio: 'São Miguel dos Campos',
          status: 'CONCLUIDA' as const,
          prioridade: 'MEDIA' as const,
          dataGravacao: '2026-08-28',
          dataEntrega: '2026-08-29',
          criadorNome: 'Igor Santos',
          quemAtribuiu: 'Igor Santos',
          responsaveis: ['Igor Santos'],
          checklist: [
            { id: 1, descricao: 'Elaborar artes', concluido: true, ordem: 1 },
            { id: 2, descricao: 'Revisar valores', concluido: true, ordem: 2 },
          ],
          arquivosFinais: [
            { id: 201, nome: 'promo-1.png', urlOuBase64: 'https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
          ],
          statusAprovacao: 'AGUARDANDO_CLIENTE' as const,
          tokenAprovacao: 'DA-APRV-SJOAO02',
          percentualConcluido: 100,
          dataCriacao: '2026-08-29T08:00:00Z',
          dataConclusao: '2026-08-29',
        },
        {
          id: 3,
          titulo: 'Artes promo',
          descricao: 'Campanha de hortifruti e açougue.',
          briefing: '11 peças no total.',
          loja: 'O supermercado Favorito',
          clienteId: 5,
          municipio: 'Marechal Deodoro',
          status: 'CONCLUIDA' as const,
          prioridade: 'MEDIA' as const,
          dataGravacao: '2026-08-26',
          dataEntrega: '2026-08-27',
          criadorNome: 'Igor Santos',
          quemAtribuiu: 'Igor Santos',
          responsaveis: ['Igor Santos'],
          checklist: [
            { id: 1, descricao: 'Diagramação', concluido: true, ordem: 1 },
          ],
          arquivosFinais: [
            { id: 301, nome: 'horti-1.png', urlOuBase64: 'https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
          ],
          statusAprovacao: 'AGUARDANDO_CLIENTE' as const,
          tokenAprovacao: 'DA-APRV-FAV01',
          percentualConcluido: 100,
          dataCriacao: '2026-08-27T08:00:00Z',
          dataConclusao: '2026-08-27',
        },
        {
          id: 4,
          titulo: 'Fotos',
          descricao: 'Ensaio fotográfico Coleção Primavera.',
          briefing: '51 fotos tratadas para catálogo e e-commerce.',
          loja: 'Closet Klivia Freitas',
          clienteId: 3,
          municipio: 'Maceió',
          status: 'CONCLUIDA' as const,
          prioridade: 'ALTA' as const,
          dataGravacao: '2026-08-20',
          dataEntrega: '2026-08-22',
          criadorNome: 'Igor Santos',
          quemAtribuiu: 'Igor Santos',
          responsaveis: ['Igor Santos', 'Ingrid Ferreira'],
          checklist: [
            { id: 1, descricao: 'Sessão fotográfica', concluido: true, ordem: 1 },
            { id: 2, descricao: 'Tratamento de cor e pele', concluido: true, ordem: 2 },
          ],
          arquivosFinais: [
            { id: 401, nome: 'foto-ensaio.jpg', urlOuBase64: 'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=800&q=80', tipo: 'IMAGEM' as const },
          ],
          statusAprovacao: 'AGUARDANDO_CLIENTE' as const,
          tokenAprovacao: 'DA-APRV-KLIVIA01',
          percentualConcluido: 100,
          dataCriacao: '2026-08-22T08:00:00Z',
          dataConclusao: '2026-08-22',
        },
        {
          id: 5,
          titulo: 'artes',
          descricao: 'Peças para redes sociais.',
          briefing: '4 artes institucionais.',
          loja: 'Mercado novo São João',
          clienteId: 2,
          municipio: 'São Miguel dos Campos',
          status: 'CONCLUIDA' as const,
          prioridade: 'MEDIA' as const,
          dataGravacao: '2026-08-20',
          dataEntrega: '2026-08-21',
          criadorNome: 'Igor Santos',
          quemAtribuiu: 'Igor Santos',
          responsaveis: ['Igor Santos'],
          checklist: [
            { id: 1, descricao: 'Arte final', concluido: true, ordem: 1 },
          ],
          statusAprovacao: 'AGUARDANDO_CLIENTE' as const,
          tokenAprovacao: 'DA-APRV-SJOAO03',
          percentualConcluido: 100,
          dataCriacao: '2026-08-21T08:00:00Z',
          dataConclusao: '2026-08-21',
        },
        {
          id: 6,
          titulo: 'video da serie',
          descricao: 'Vídeo dinâmico de treino e equipamentos da Titanium.',
          briefing: 'Captação 4K com cortes rápidos e trilha energética.',
          loja: 'ACADEMIA TITANIUM',
          clienteId: 1,
          municipio: 'São Miguel dos Campos',
          status: 'CONCLUIDA' as const,
          prioridade: 'ALTA' as const,
          dataGravacao: '2026-08-19',
          dataEntrega: '2026-08-21',
          criadorNome: 'Igor Santos',
          quemAtribuiu: 'Igor Santos',
          responsaveis: ['Igor Santos'],
          checklist: [
            { id: 1, descricao: 'Gravação na academia', concluido: true, ordem: 1 },
            { id: 2, descricao: 'Edição e color grading', concluido: true, ordem: 2 },
          ],
          arquivosFinais: [
            { id: 601, nome: 'reels-titanium.mp4', urlOuBase64: 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=800&q=80', tipo: 'VIDEO' as const },
          ],
          statusAprovacao: 'AGUARDANDO_CLIENTE' as const,
          tokenAprovacao: 'DA-APRV-TITAN01',
          percentualConcluido: 100,
          dataCriacao: '2026-08-21T08:00:00Z',
          dataConclusao: '2026-08-21',
        },
        {
          id: 7,
          titulo: 'Artes promo',
          descricao: 'Artes aprovadas do Mercado Novo São João.',
          briefing: 'Encarte institucional aprovado.',
          loja: 'Mercado novo São João',
          clienteId: 2,
          municipio: 'São Miguel dos Campos',
          status: 'CONCLUIDA' as const,
          prioridade: 'MEDIA' as const,
          dataGravacao: '2026-08-18',
          dataEntrega: '2026-08-19',
          criadorNome: 'Igor Santos',
          quemAtribuiu: 'Igor Santos',
          responsaveis: ['Igor Santos'],
          checklist: [
            { id: 1, descricao: 'Artes entregues', concluido: true, ordem: 1 },
          ],
          statusAprovacao: 'APROVADO' as const,
          tokenAprovacao: 'DA-APRV-SJOAO04',
          percentualConcluido: 100,
          dataCriacao: '2026-08-19T08:00:00Z',
          dataConclusao: '2026-08-19',
        },
        {
          id: 8,
          titulo: 'Caminhada Beto',
          descricao: 'Cobertura em vídeo e fotos da caminhada popular com apoiadores.',
          briefing: 'Instruções de gravação, roteiro e formatos esperados.',
          loja: 'Novo São João',
          clienteId: 2,
          municipio: 'São Miguel dos Campos',
          status: 'A_FAZER' as const,
          prioridade: 'ALTA' as const,
          dataGravacao: '2026-09-13',
          dataEntrega: '2026-09-16',
          criadorNome: 'Igor Santos',
          quemAtribuiu: 'Igor Santos',
          responsaveis: ['Igor Santos', 'Edyllaine Silva', 'Ingrid Ferreira'],
          checklist: [
            { id: 1, descricao: 'Planejar conteúdo da semana', concluido: false, ordem: 1 },
            { id: 2, descricao: 'Criar roteiro dos vídeos / Reels', concluido: false, ordem: 2 },
            { id: 3, descricao: 'Gravar vídeos e takes no set', concluido: false, ordem: 3 },
            { id: 4, descricao: 'Editar e finalizar Reels', concluido: false, ordem: 4 },
            { id: 5, descricao: 'Produzir artes para feed e Stories', concluido: false, ordem: 5 },
            { id: 6, descricao: 'Revisar textos e identidade visual', concluido: false, ordem: 6 },
          ],
          arquivosFinais: [],
          observacoes: [],
          statusAprovacao: 'AGUARDANDO_CLIENTE' as const,
          tokenAprovacao: 'DA-APRV-BETO01',
          percentualConcluido: 0,
          dataCriacao: '2026-09-10T10:00:00Z',
        },
        {
          id: 9,
          titulo: 'Produção de conteúdo de marketing',
          descricao: 'Campanha de lançamento Coleção Primavera com modelos e bastidores.',
          briefing: 'Produzir todo o material de divulgação da campanha.',
          loja: 'Sr. Junior',
          clienteId: 6,
          municipio: 'São Miguel dos Campos',
          status: 'CONCLUIDA' as const,
          prioridade: 'ALTA' as const,
          dataGravacao: '2026-08-15',
          dataEntrega: '2026-08-18',
          criadorNome: 'Ingrid Ferreira',
          quemAtribuiu: 'Ingrid Ferreira',
          responsaveis: ['Edyllaine Silva', 'Igor Santos', 'Ingrid Ferreira', 'Williane Samia'],
          checklist: [
            { id: 1, descricao: 'Captação no local', concluido: true, ordem: 1 },
            { id: 2, descricao: 'Edição de vídeo', concluido: true, ordem: 2 },
          ],
          statusAprovacao: 'AGUARDANDO_CLIENTE' as const,
          tokenAprovacao: 'DA-APRV-SRJR01',
          percentualConcluido: 100,
          dataCriacao: '2026-08-18T08:00:00Z',
          dataConclusao: '2026-08-18',
        },
      ],
      roteiros: [
        {
          id: 1,
          titulo: 'LANÇAMENTO COLEÇÃO PRIMAVERA | ATELIÊ DA YSA',
          loja: 'ATELIÊ DA YSA',
          clienteNome: 'ATELIÊ DA YSA',
          tarefaId: 1,
          tarefaTitulo: 'Produção de conteúdo de marketing',
          criadorNome: 'LUCAS MATHEUS',
          dataGravacao: '2026-08-20',
          conteudoScript: 'Criação de roteiro completo para a campanha primaveril.',
          cenas: [
            { ordem: 1, descricao: 'CENA 1: Modelo entrando no ateliê em slow-motion admirando os vestidos.' },
            { ordem: 2, descricao: 'CENA 2: Close-up nos tecidos bordados à mão e detalhes da costura autêntica.' },
            { ordem: 3, descricao: 'CENA 3: Ysa explicando a inspiração botânica da nova coleção.' },
          ],
          instrucoesCamera: 'Lente 50mm f/1.4, iluminação difusa dourada natural, 60fps para câmera lenta.',
          observacoesSet: 'Gravar nos horários de luz suave (manhã ou fim de tarde).',
          status: 'CONCLUIDO' as const,
          feito: true,
          dataCriacao: '2026-08-20T08:00:00Z',
          dataConclusao: '2026-08-20',
        },
        {
          id: 2,
          titulo: 'NOVO ESPAÇO FITNESS | JM MODA FITNESS',
          loja: 'JM MODA FITNESS',
          clienteNome: 'JM MODA FITNESS',
          tarefaId: 8,
          tarefaTitulo: 'Gravação Coleção Fitness Inverno',
          criadorNome: 'LUCAS MATHEUS',
          dataGravacao: '2026-09-15',
          conteudoScript: 'Roteiro de apresentação da nova coleção em academia moderna.',
          cenas: [
            { ordem: 1, descricao: 'CENA 1: Atleta alongando na área externa com iluminação do nascer do sol.' },
            { ordem: 2, descricao: 'CENA 2: Movimentos dinâmicos com a nova linha de leggings sem costura.' },
            { ordem: 3, descricao: 'CENA 3: Encerramento com logotipo da JM Moda Fitness em destaque.' },
          ],
          instrucoesCamera: 'Gimbal estabilizado 4K 120fps para takes de ação, lente 24-70mm.',
          observacoesSet: 'Levar rebatedores e baterias extras.',
          status: 'PENDENTE' as const,
          feito: false,
          dataCriacao: '2026-09-02T10:00:00Z',
        },
        {
          id: 3,
          titulo: 'Vídeo da Série - Treino Funcional | ACADEMIA TITANIUM',
          loja: 'ACADEMIA TITANIUM',
          clienteNome: 'ACADEMIA TITANIUM',
          tarefaId: 6,
          tarefaTitulo: 'video da serie',
          criadorNome: 'Igor Santos',
          dataGravacao: '2026-09-18',
          conteudoScript: 'Série de reels com dicas rápidas dos personais da Titanium.',
          cenas: [
            { ordem: 1, descricao: 'CENA 1: Apresentação do personal trainer e do tema do dia.' },
            { ordem: 2, descricao: 'CENA 2: Demonstração do exercício com correção postural.' },
            { ordem: 3, descricao: 'CENA 3: Chamada para matricular-se na Academia Titanium.' },
          ],
          instrucoesCamera: 'Microfone lapela sem fio, iluminação led bicolor com softbox.',
          observacoesSet: 'Gravar 3 vídeos na mesma sessão.',
          status: 'PENDENTE' as const,
          feito: false,
          dataCriacao: '2026-09-08T14:00:00Z',
        },
      ],
      logos: [
        {
          id: 1,
          clienteNome: 'ACADEMIA TITANIUM',
          variante: 'Versão Principal Escura',
          formato: 'PNG',
          arquivoUrlOuBase64: 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=300&q=80',
          tamanho: '2.4 MB',
          corPrimaria: '#f97316',
          dataUpload: '2026-08-01T10:00:00Z',
        },
        {
          id: 2,
          clienteNome: 'ATELIÊ DA YSA',
          variante: 'Vetor Transparente',
          formato: 'SVG',
          arquivoUrlOuBase64: 'https://images.unsplash.com/photo-1558769132-cb1aea458c5e?auto=format&fit=crop&w=300&q=80',
          tamanho: '850 KB',
          corPrimaria: '#ec4899',
          dataUpload: '2026-08-05T14:30:00Z',
        },
        {
          id: 3,
          clienteNome: 'MERCADO NOVO SÃO JOÃO',
          variante: 'Horizontal Colorida',
          formato: 'PNG',
          arquivoUrlOuBase64: 'https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=300&q=80',
          tamanho: '3.1 MB',
          corPrimaria: '#ef4444',
          dataUpload: '2026-08-10T09:15:00Z',
        },
        {
          id: 4,
          clienteNome: 'SR. JUNIOR',
          variante: 'Monocromático Preto & Branco',
          formato: 'PNG',
          arquivoUrlOuBase64: 'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?auto=format&fit=crop&w=300&q=80',
          tamanho: '1.8 MB',
          corPrimaria: '#1e293b',
          dataUpload: '2026-08-12T11:00:00Z',
        },
        {
          id: 5,
          clienteNome: 'VIVAMAIS MERCADO NATURAL',
          variante: 'Vetor Verde Esmeralda',
          formato: 'SVG',
          arquivoUrlOuBase64: 'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=300&q=80',
          tamanho: '920 KB',
          corPrimaria: '#10b981',
          dataUpload: '2026-08-15T16:45:00Z',
        },
      ],
      eventos: [
        {
          id: 1,
          nome: 'BARRA RUN 2026',
          slug: 'barra-run-2026',
          localizacao: 'Barra de São Miguel - AL, Brasil',
          dataEvento: '2026-08-01',
          horario: '05:00 às 09:00',
          precoFotoVendida: 10.0,
          publicoEstimado: 500,
          bannerUrl: 'https://images.unsplash.com/photo-1530549387789-4c1017266635?auto=format&fit=crop&w=1200&q=80',
          descricao: 'Concentração Praça em frente à Prefeitura. Cobertura de corrida de rua com proteção de marca dágua e venda individual.',
          status: 'PUBLICADO',
          fotos: [
            {
              id: 1,
              codigoFoto: 'RUN-001',
              titulo: 'Largada Pelotão de Elite Feminino e Masculino',
              urlOuBase64: 'https://images.unsplash.com/photo-1552674605-db6ffd4facb5?auto=format&fit=crop&w=1000&q=80',
              preco: 10.0,
              marcaDaguaTexto: 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
              visualizacoes: 142,
              vendas: 18,
            },
            {
              id: 2,
              codigoFoto: 'RUN-002',
              titulo: 'Atletas na curva da praia em ritmo intenso',
              urlOuBase64: 'https://images.unsplash.com/photo-1571008887538-b36bb32f4571?auto=format&fit=crop&w=1000&q=80',
              preco: 10.0,
              marcaDaguaTexto: 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
              visualizacoes: 98,
              vendas: 12,
            },
            {
              id: 3,
              codigoFoto: 'RUN-003',
              titulo: 'Chegada emocionante e celebração dos 10K',
              urlOuBase64: 'https://images.unsplash.com/photo-1461896836934-ffe607ba8211?auto=format&fit=crop&w=1000&q=80',
              preco: 10.0,
              marcaDaguaTexto: 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
              visualizacoes: 215,
              vendas: 31,
            },
          ],
        },
        {
          id: 2,
          nome: 'Fotos Loja 3 anos',
          slug: 'fotos-loja-3-anos',
          localizacao: 'Maceió - AL, Brasil',
          dataEvento: '2026-08-20',
          horario: '14:00 às 20:00',
          precoFotoVendida: 15.0,
          publicoEstimado: 200,
          bannerUrl: 'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=1200&q=80',
          descricao: 'Aniversário de 3 anos do Closet Klivia Freitas. Fotos exclusivas com convidados, modelos e looks especiais.',
          status: 'PUBLICADO',
          fotos: [
            {
              id: 4,
              codigoFoto: 'KLV-001',
              titulo: 'Klivia Freitas com convidados de honra',
              urlOuBase64: 'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=1000&q=80',
              preco: 15.0,
              marcaDaguaTexto: 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
              visualizacoes: 85,
              vendas: 14,
            },
          ],
        },
        {
          id: 3,
          nome: 'VIVA MAIS - LANÇAMENTO MORNING SHOT',
          slug: 'viva-mais',
          localizacao: 'Arapiraca - AL, Brasil',
          dataEvento: '2026-08-25',
          horario: '08:00 às 13:00',
          precoFotoVendida: 12.0,
          publicoEstimado: 150,
          bannerUrl: 'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=1200&q=80',
          descricao: 'Lançamento oficial do suplemento Morning Shot com degustação e influenciadores.',
          status: 'PUBLICADO',
          fotos: [
            {
              id: 5,
              codigoFoto: 'VM-001',
              titulo: 'Degustação e exibição de produtos fit',
              urlOuBase64: 'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=1000&q=80',
              preco: 12.0,
              marcaDaguaTexto: 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
              visualizacoes: 64,
              vendas: 9,
            },
          ],
        },
      ],
      vendas: [
        {
          id: 1,
          codigoVenda: '#257168787',
          clienteNome: 'Adrianny evelyn',
          clienteEmail: 'adrianny.evelyn@email.com',
          eventoNome: 'BARRA RUN 2026',
          qtdFotos: 2,
          qtdVideos: 0,
          valorTotal: 22.5,
          status: 'PAGO' as const,
          dataDisponivelInfo: 'Disponível em 29/07/2026',
          dataVenda: '2026-07-27T19:42:00Z',
        },
        {
          id: 2,
          codigoVenda: '#257157586',
          clienteNome: 'Jadiel soares',
          clienteEmail: 'jadiel.soares@email.com',
          eventoNome: 'BARRA RUN 2026',
          qtdFotos: 1,
          qtdVideos: 0,
          valorTotal: 9.0,
          status: 'PAGO' as const,
          dataDisponivelInfo: 'Disponível em 29/07/2026',
          dataVenda: '2026-07-27T19:19:00Z',
        },
        {
          id: 3,
          codigoVenda: '#257146110',
          clienteNome: 'Emanoella esterfanny',
          clienteEmail: 'emanoella@email.com',
          eventoNome: 'BARRA RUN 2026',
          qtdFotos: 1,
          qtdVideos: 0,
          valorTotal: 9.0,
          status: 'PAGO' as const,
          dataDisponivelInfo: 'Disponível em 29/07/2026',
          dataVenda: '2026-07-27T15:00:00Z',
        },
        {
          id: 4,
          codigoVenda: '#257145261',
          clienteNome: 'Michellandy melo dos santos',
          clienteEmail: 'michellandy@email.com',
          eventoNome: 'BARRA RUN 2026',
          qtdFotos: 6,
          qtdVideos: 0,
          valorTotal: 54.0,
          status: 'PAGO' as const,
          dataDisponivelInfo: 'Disponível em 29/07/2026',
          dataVenda: '2026-07-27T14:43:00Z',
        },
        {
          id: 5,
          codigoVenda: '#257139903',
          clienteNome: 'Jessyka marques',
          clienteEmail: 'jessyka@email.com',
          eventoNome: 'BARRA RUN 2026',
          qtdFotos: 1,
          qtdVideos: 0,
          valorTotal: 9.0,
          status: 'PAGO' as const,
          dataDisponivelInfo: 'Disponível em 29/07/2026',
          dataVenda: '2026-07-27T13:03:00Z',
        },
      ],
      despesas: [
        {
          id: 1,
          descricao: 'Assinatura Adobe Creative Cloud & Plugins',
          categoria: 'Software & Ferramentas',
          valor: 450.0,
          dataDespesa: '2026-09-02',
          formaPagamento: 'Cartão de Crédito',
          status: 'PAGO' as const,
          observacoes: 'Renovação mensal equipe de criação',
        },
        {
          id: 2,
          descricao: 'Hospedagem de Servidores & Cloud Storage',
          categoria: 'Infraestrutura',
          valor: 280.0,
          dataDespesa: '2026-09-04',
          formaPagamento: 'Cartão de Crédito',
          status: 'PAGO' as const,
          observacoes: 'Armazenamento de fotos em nuvem',
        },
        {
          id: 3,
          descricao: 'Combustível para Cobertura Externa de Eventos',
          categoria: 'Logística',
          valor: 180.0,
          dataDespesa: '2026-09-06',
          formaPagamento: 'PIX',
          status: 'PAGO' as const,
          observacoes: 'Deslocamento até Barra de São Miguel',
        },
      ],
    };
  }
}
