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
} from '../models';

@Injectable({
  providedIn: 'root',
})
export class MockStorageService {
  private readonly STORAGE_KEY = 'designart_mock_db_v1';

  private db: {
    tarefas: Tarefa[];
    roteiros: Roteiro[];
    logos: LogoCliente[];
    eventos: Evento[];
    vendas: VendaFoto[];
    despesas: Despesa[];
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
      aFazer: aFazer || 1,
      emDesenvolvimento: emDev || 1,
      emRevisaoOuNaoHomologada: emRevisao || 1,
      atrasadas: atrasadas || 1,
      concluidas: concluidas || 0,
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
          dataEntrega: '2026-09-08',
          prioridade: 'ALTA',
          status: 'EM_DESENVOLVIMENTO',
          mensagem: 'Entrega prevista para os próximos dias.',
        },
      ],
      producaoMensal: [
        { mes: 'Janeiro', mesAbreviado: 'Jan', atendimentos: 1250, concluidos: 1100 },
        { mes: 'Fevereiro', mesAbreviado: 'Fev', atendimentos: 1420, concluidos: 1300 },
        { mes: 'Março', mesAbreviado: 'Mar', atendimentos: 1890, concluidos: 1750 },
        { mes: 'Abril', mesAbreviado: 'Abr', atendimentos: 2100, concluidos: 1980 },
        { mes: 'Maio', mesAbreviado: 'Mai', atendimentos: 2450, concluidos: 2300 },
        { mes: 'Junho', mesAbreviado: 'Jun', atendimentos: 3100, concluidos: 2950 },
        { mes: 'Julho', mesAbreviado: 'Jul', atendimentos: 3850, concluidos: 3600 },
        { mes: 'Agosto', mesAbreviado: 'Ago', atendimentos: 2900, concluidos: 2700 },
        { mes: 'Setembro', mesAbreviado: 'Set', atendimentos: 1800, concluidos: 1600 },
        { mes: 'Outubro', mesAbreviado: 'Out', atendimentos: 0, concluidos: 0 },
        { mes: 'Novembro', mesAbreviado: 'Nov', atendimentos: 0, concluidos: 0 },
        { mes: 'Dezembro', mesAbreviado: 'Dez', atendimentos: 0, concluidos: 0 },
      ],
      rankingColaboradores: [
        { nome: 'Lucas Matheus', totalTarefasConcluidas: 580, cargo: 'Roteirista & Estrategista' },
        { nome: 'Edyllaine Silva', totalTarefasConcluidas: 512, cargo: 'Social Media' },
        { nome: 'Igor Santos', totalTarefasConcluidas: 460, cargo: 'Editor de Vídeo' },
        { nome: 'Maiara Callind', totalTarefasConcluidas: 390, cargo: 'Designer Gráfico' },
        { nome: 'Osmar Israel', totalTarefasConcluidas: 340, cargo: 'Filmmaker' },
        { nome: 'Ingrid Ferreira', totalTarefasConcluidas: 295, cargo: 'Fotógrafa' },
      ],
      ultimasVendas: this.db.vendas.slice(0, 7),
    };
  }

  // === TAREFAS ===
  getTarefas(loja?: string, status?: string, prioridade?: string): Tarefa[] {
    return this.db.tarefas.filter((t) => {
      let match = true;
      if (loja && !t.loja.toLowerCase().includes(loja.toLowerCase()) && !t.titulo.toLowerCase().includes(loja.toLowerCase())) {
        match = false;
      }
      if (status && t.status !== status) {
        match = false;
      }
      if (prioridade && t.prioridade !== prioridade) {
        match = false;
      }
      return match;
    });
  }

  getTarefaById(id: number): Tarefa | undefined {
    return this.db.tarefas.find((t) => t.id === id);
  }

  createTarefa(tarefa: Partial<Tarefa>): Tarefa {
    const nextId = this.db.tarefas.reduce((max, t) => Math.max(max, t.id || 0), 0) + 1;
    const checklist = (tarefa.checklist || []).map((item, idx) => ({
      id: idx + 1,
      descricao: item.descricao,
      concluido: !!item.concluido,
      ordem: idx + 1,
    }));
    const totalItens = checklist.length;
    const concluidos = checklist.filter((i) => i.concluido).length;
    const percentual = totalItens > 0 ? Math.round((concluidos / totalItens) * 100) : 0;

    const nova: Tarefa = {
      id: nextId,
      titulo: tarefa.titulo || 'Nova Demanda',
      descricao: tarefa.descricao || '',
      briefing: tarefa.briefing || '',
      loja: (tarefa.loja || 'JM MODA FITNESS').toUpperCase(),
      status: tarefa.status || 'A_FAZER',
      prioridade: tarefa.prioridade || 'MEDIA',
      dataGravacao: tarefa.dataGravacao,
      dataEntrega: tarefa.dataEntrega,
      criadorNome: tarefa.criadorNome || 'Lucas Matheus',
      responsaveis: tarefa.responsaveis || ['Edyllaine Silva'],
      checklist,
      percentualConcluido: percentual,
      dataCriacao: new Date().toISOString().split('T')[0],
    };
    this.db.tarefas.unshift(nova);
    this.save();
    return nova;
  }

  updateTarefa(id: number, alteracoes: Partial<Tarefa>): Tarefa {
    const idx = this.db.tarefas.findIndex((t) => t.id === id);
    if (idx !== -1) {
      const existing = this.db.tarefas[idx];
      const updated: Tarefa = { ...existing, ...alteracoes };
      if (updated.checklist) {
        const total = updated.checklist.length;
        const concl = updated.checklist.filter((i) => i.concluido).length;
        updated.percentualConcluido = total > 0 ? Math.round((concl / total) * 100) : 0;
      }
      this.db.tarefas[idx] = updated;
      this.save();
      return updated;
    }
    throw new Error('Tarefa não encontrada');
  }

  toggleChecklistItem(tarefaId: number, itemId: number): Tarefa {
    const tarefa = this.db.tarefas.find((t) => t.id === tarefaId);
    if (tarefa && tarefa.checklist) {
      const item = tarefa.checklist.find((i) => i.id === itemId);
      if (item) {
        item.concluido = !item.concluido;
        const total = tarefa.checklist.length;
        const concl = tarefa.checklist.filter((i) => i.concluido).length;
        tarefa.percentualConcluido = total > 0 ? Math.round((concl / total) * 100) : 0;
        this.save();
        return tarefa;
      }
    }
    throw new Error('Checklist item não encontrado');
  }

  deleteTarefa(id: number): void {
    this.db.tarefas = this.db.tarefas.filter((t) => t.id !== id);
    this.save();
  }

  // === ROTEIROS ===
  getRoteiros(loja?: string): Roteiro[] {
    return this.db.roteiros.filter((r) => {
      if (loja && !r.loja.toLowerCase().includes(loja.toLowerCase()) && !r.titulo.toLowerCase().includes(loja.toLowerCase())) {
        return false;
      }
      return true;
    });
  }

  createRoteiro(roteiro: Partial<Roteiro>): Roteiro {
    const nextId = this.db.roteiros.reduce((max, r) => Math.max(max, r.id || 0), 0) + 1;
    const novo: Roteiro = {
      id: nextId,
      titulo: (roteiro.titulo || 'NOVO ROTEIRO').toUpperCase(),
      loja: (roteiro.loja || 'JM MODA FITNESS').toUpperCase(),
      criadorNome: (roteiro.criadorNome || 'LUCAS MATHEUS').toUpperCase(),
      dataGravacao: roteiro.dataGravacao,
      conteudoScript: roteiro.conteudoScript || '',
      observacoesSet: roteiro.observacoesSet || '',
      status: roteiro.status || 'PENDENTE',
      feito: !!roteiro.feito,
      dataCriacao: new Date().toISOString().split('T')[0],
    };
    this.db.roteiros.unshift(novo);
    this.save();
    return novo;
  }

  updateRoteiro(id: number, alteracoes: Partial<Roteiro>): Roteiro {
    const idx = this.db.roteiros.findIndex((r) => r.id === id);
    if (idx !== -1) {
      this.db.roteiros[idx] = { ...this.db.roteiros[idx], ...alteracoes };
      this.save();
      return this.db.roteiros[idx];
    }
    throw new Error('Roteiro não encontrado');
  }

  toggleRoteiroConcluido(id: number): Roteiro {
    const r = this.db.roteiros.find((item) => item.id === id);
    if (r) {
      r.feito = !r.feito;
      r.status = r.feito ? 'CONCLUIDO' : 'EM_GRAVACAO';
      this.save();
      return r;
    }
    throw new Error('Roteiro não encontrado');
  }

  deleteRoteiro(id: number): void {
    this.db.roteiros = this.db.roteiros.filter((r) => r.id !== id);
    this.save();
  }

  // === LOGOS ===
  getLogos(clienteNome?: string): LogoCliente[] {
    return this.db.logos.filter((l) => {
      if (clienteNome && !l.clienteNome.toLowerCase().includes(clienteNome.toLowerCase())) {
        return false;
      }
      return true;
    });
  }

  createLogo(logo: Partial<LogoCliente>): LogoCliente {
    const nextId = this.db.logos.reduce((max, l) => Math.max(max, l.id || 0), 0) + 1;
    const nova: LogoCliente = {
      id: nextId,
      clienteNome: (logo.clienteNome || 'NOVO CLIENTE').toUpperCase(),
      variante: logo.variante || 'Logo Principal Colorida',
      formato: logo.formato || 'PNG',
      tamanho: logo.tamanho || '1.5 MB',
      corPrimaria: logo.corPrimaria || '#6366F1',
      arquivoUrlOuBase64:
        logo.arquivoUrlOuBase64 ||
        'https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=400&auto=format&fit=crop&q=60',
      dataUpload: new Date().toISOString().split('T')[0],
    };
    this.db.logos.unshift(nova);
    this.save();
    return nova;
  }

  deleteLogo(id: number): void {
    this.db.logos = this.db.logos.filter((l) => l.id !== id);
    this.save();
  }

  // === EVENTOS & FOTOS ===
  getEventos(): Evento[] {
    return this.db.eventos;
  }

  getEventoById(id: number): Evento | undefined {
    return this.db.eventos.find((e) => e.id === id);
  }

  createEvento(evento: Partial<Evento>): Evento {
    const nextId = this.db.eventos.reduce((max, e) => Math.max(max, e.id || 0), 0) + 1;
    const novo: Evento = {
      id: nextId,
      nome: (evento.nome || 'NOVO EVENTO').toUpperCase(),
      localizacao: evento.localizacao || 'Alagoas, Brasil',
      dataEvento: evento.dataEvento || new Date().toISOString().split('T')[0],
      horario: evento.horario || '08:00 às 12:00',
      precoFotoVendida: evento.precoFotoVendida || 10.0,
      publicoEstimado: evento.publicoEstimado || 300,
      bannerUrl:
        evento.bannerUrl ||
        'https://images.unsplash.com/photo-1452626038306-9aae5e071dd3?w=800&auto=format&fit=crop&q=60',
      descricao: evento.descricao || '',
      status: 'PUBLICADO',
      fotos: [],
    };
    this.db.eventos.unshift(novo);
    this.save();
    return novo;
  }

  addFotoEvento(eventoId: number, foto: Partial<FotoEvento>): FotoEvento {
    const evento = this.db.eventos.find((e) => e.id === eventoId);
    if (evento) {
      if (!evento.fotos) evento.fotos = [];
      const nextId = evento.fotos.reduce((max, f) => Math.max(max, f.id || 0), 0) + 1;
      const nova: FotoEvento = {
        id: nextId,
        codigoFoto: `FOTO-${String(nextId).padStart(3, '0')}`,
        titulo: foto.titulo || `Foto #${nextId}`,
        urlOuBase64: foto.urlOuBase64 || 'https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=600',
        preco: foto.preco || evento.precoFotoVendida || 10.0,
        marcaDaguaTexto: foto.marcaDaguaTexto || 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
        visualizacoes: 0,
        vendas: 0,
      };
      evento.fotos.push(nova);
      this.save();
      return nova;
    }
    throw new Error('Evento não encontrado');
  }

  deleteFotoEvento(fotoId: number): void {
    this.db.eventos.forEach((e) => {
      if (e.fotos) {
        e.fotos = e.fotos.filter((f) => f.id !== fotoId);
      }
    });
    this.save();
  }

  deleteEvento(id: number): void {
    this.db.eventos = this.db.eventos.filter((e) => e.id !== id);
    this.save();
  }

  // === VENDAS ===
  getVendas(status?: string): VendaFoto[] {
    return this.db.vendas.filter((v) => {
      if (status && v.status !== status) return false;
      return true;
    });
  }

  createVenda(venda: Partial<VendaFoto>): VendaFoto {
    const nextId = this.db.vendas.reduce((max, v) => Math.max(max, v.id || 0), 0) + 1;
    const cod = `#${Math.floor(100000000 + Math.random() * 900000000)}`;
    const nova: VendaFoto = {
      id: nextId,
      codigoVenda: cod,
      clienteNome: venda.clienteNome || 'Cliente Anônimo',
      clienteEmail: venda.clienteEmail || 'cliente@email.com',
      eventoNome: venda.eventoNome || 'BARRA RUN 2026',
      qtdFotos: venda.qtdFotos || 1,
      qtdVideos: venda.qtdVideos || 0,
      valorTotal: venda.valorTotal || 10.0,
      status: venda.status || 'PAGO',
      dataDisponivelInfo: 'Disponível Imediatamente',
      dataVenda: new Date().toISOString(),
    };
    this.db.vendas.unshift(nova);
    this.save();
    return nova;
  }

  updateVendaStatus(id: number, status: 'PAGO' | 'PENDENTE' | 'ATRASADO'): VendaFoto {
    const v = this.db.vendas.find((item) => item.id === id);
    if (v) {
      v.status = status;
      this.save();
      return v;
    }
    throw new Error('Venda não encontrada');
  }

  deleteVenda(id: number): void {
    this.db.vendas = this.db.vendas.filter((v) => v.id !== id);
    this.save();
  }

  // === DESPESAS & FLUXO DE CAIXA ===
  getDespesas(): Despesa[] {
    return this.db.despesas;
  }

  createDespesa(despesa: Partial<Despesa>): Despesa {
    const nextId = this.db.despesas.reduce((max, d) => Math.max(max, d.id || 0), 0) + 1;
    const nova: Despesa = {
      id: nextId,
      descricao: despesa.descricao || 'Despesa Operacional',
      categoria: despesa.categoria || 'Geral',
      valor: despesa.valor || 100.0,
      dataDespesa: despesa.dataDespesa || new Date().toISOString().split('T')[0],
      formaPagamento: despesa.formaPagamento || 'PIX',
      status: despesa.status || 'PAGO',
      observacoes: despesa.observacoes || '',
    };
    this.db.despesas.unshift(nova);
    this.save();
    return nova;
  }

  updateDespesa(id: number, alteracoes: Partial<Despesa>): Despesa {
    const idx = this.db.despesas.findIndex((d) => d.id === id);
    if (idx !== -1) {
      this.db.despesas[idx] = { ...this.db.despesas[idx], ...alteracoes };
      this.save();
      return this.db.despesas[idx];
    }
    throw new Error('Despesa não encontrada');
  }

  deleteDespesa(id: number): void {
    this.db.despesas = this.db.despesas.filter((d) => d.id !== id);
    this.save();
  }

  getFluxoCaixa(): FluxoCaixa {
    const totalEntradas = this.db.vendas
      .filter((v) => v.status === 'PAGO')
      .reduce((acc, v) => acc + (v.valorTotal || 0), 0);

    const totalSaidas = this.db.despesas
      .filter((d) => d.status === 'PAGO')
      .reduce((acc, d) => acc + (d.valor || 0), 0);

    return {
      totalEntradas: totalEntradas || 148.5,
      totalSaidas: totalSaidas || 2210.0,
      lucroLiquido: totalEntradas - totalSaidas,
      comparativosMensais: [
        { mes: 'Janeiro', mesAbreviado: 'Jan', entradas: 4200, saidas: 2100, lucro: 2100 },
        { mes: 'Fevereiro', mesAbreviado: 'Fev', entradas: 4800, saidas: 2300, lucro: 2500 },
        { mes: 'Março', mesAbreviado: 'Mar', entradas: 5600, saidas: 2700, lucro: 2900 },
        { mes: 'Abril', mesAbreviado: 'Abr', entradas: 6200, saidas: 2900, lucro: 3300 },
        { mes: 'Maio', mesAbreviado: 'Mai', entradas: 6900, saidas: 3100, lucro: 3800 },
        { mes: 'Junho', mesAbreviado: 'Jun', entradas: 7800, saidas: 3400, lucro: 4400 },
        { mes: 'Julho', mesAbreviado: 'Jul', entradas: 8500, saidas: 3800, lucro: 4700 },
        { mes: 'Agosto', mesAbreviado: 'Ago', entradas: 7100, saidas: 3200, lucro: 3900 },
      ],
      ultimasDespesas: this.db.despesas.slice(0, 5),
    };
  }

  // === RELATÓRIOS MENSAL ===
  getRelatorioMensal(loja?: string, mes?: number, ano?: number): RelatorioMensalItem[] {
    return this.db.tarefas
      .filter((t) => {
        if (loja && !t.loja.toLowerCase().includes(loja.toLowerCase())) return false;
        return true;
      })
      .map((t) => ({
        tarefaId: t.id || 1,
        tituloDemanda: t.titulo,
        loja: t.loja,
        criadorNome: t.criadorNome || 'Lucas Matheus',
        participantes: t.responsaveis || ['Edyllaine Silva'],
        status: t.status,
        prioridade: t.prioridade,
        dataEntrega: t.dataEntrega || '2026-07-17',
        percentualConcluido: t.percentualConcluido || 0,
        mesAno: `${mes || 7}/${ano || 2026}`,
      }));
  }

  // === SEED DATA ===
  private getSeedData() {
    const tarefas: Tarefa[] = [
      {
        id: 1,
        titulo: 'Produção de conteúdo de marketing',
        loja: 'ATELIÊ DA YSA',
        status: 'EM_DESENVOLVIMENTO',
        prioridade: 'ALTA',
        dataGravacao: '2026-07-10',
        dataEntrega: '2026-07-17',
        criadorNome: 'Lucas Matheus',
        responsaveis: ['Edyllaine Silva', 'Igor Santos', 'Ingrid Ferreira'],
        briefing:
          'Produzir todo o material de divulgação de campanha. Atividades: Criar o roteiro dos vídeos (Reels), gravar cenas, editar e finalizar Reels, produzir Stories, criar artes para feed e Stories, enviar para aprovação e entregar arquivos finais em alta qualidade.',
        checklist: [
          { id: 1, descricao: 'Planejar conteúdo da semana', concluido: true, ordem: 1 },
          { id: 2, descricao: 'Criar roteiro dos Reels', concluido: true, ordem: 2 },
          { id: 3, descricao: 'Gravar vídeos', concluido: false, ordem: 3 },
          { id: 4, descricao: 'Editar Reels', concluido: false, ordem: 4 },
          { id: 5, descricao: 'Criar Stories', concluido: false, ordem: 5 },
          { id: 6, descricao: 'Produzir artes para feed e Stories', concluido: false, ordem: 6 },
          { id: 7, descricao: 'Revisar textos e identidade visual', concluido: false, ordem: 7 },
          { id: 8, descricao: 'Live', concluido: false, ordem: 8 },
          { id: 9, descricao: 'Enviar para aprovação', concluido: false, ordem: 9 },
          { id: 10, descricao: 'Realizar ajustes', concluido: false, ordem: 10 },
          { id: 11, descricao: 'Agendar ou entregar material final', concluido: false, ordem: 11 },
        ],
        percentualConcluido: 18,
        dataCriacao: '2026-07-01',
      },
      {
        id: 2,
        titulo: 'Gravação Coleção Fitness Inverno',
        loja: 'JM MODA FITNESS',
        status: 'EM_REVISAO',
        prioridade: 'ALTA',
        dataGravacao: '2026-06-05',
        dataEntrega: '2026-06-15',
        criadorNome: 'Lucas Matheus',
        responsaveis: ['Osmar Israel', 'Maiara Callind'],
        briefing: 'Gravação dos takes em 4K e edição dinâmica com transições rápidas.',
        checklist: [
          { id: 1, descricao: 'Elaborar Roteiro', concluido: true, ordem: 1 },
          { id: 2, descricao: 'Gravação no Set', concluido: true, ordem: 2 },
          { id: 3, descricao: 'Edição e Color Grading', concluido: true, ordem: 3 },
          { id: 4, descricao: 'Aprovação do Cliente', concluido: false, ordem: 4 },
        ],
        percentualConcluido: 75,
        dataCriacao: '2026-06-01',
      },
      {
        id: 3,
        titulo: 'Reformulação de Catálogo Digital',
        loja: 'ÓTICAS PRIME',
        status: 'ATRASADA',
        prioridade: 'URGENTE',
        dataEntrega: '2026-08-30',
        criadorNome: 'Lucas Matheus',
        responsaveis: ['Rebeca Magalhães', 'Wythcel Carvalho'],
        briefing: 'Atualização dos modelos de óculos de sol da nova estação.',
        checklist: [
          { id: 1, descricao: 'Fotografar armações', concluido: true, ordem: 1 },
          { id: 2, descricao: 'Tratamento de imagens', concluido: false, ordem: 2 },
          { id: 3, descricao: 'Diagramação do PDF', concluido: false, ordem: 3 },
        ],
        percentualConcluido: 33,
        dataCriacao: '2026-08-15',
      },
      {
        id: 4,
        titulo: 'Roteirização e Captação de Depoimentos',
        loja: 'JM MODA FITNESS',
        status: 'EM_DESENVOLVIMENTO',
        prioridade: 'ALTA',
        dataEntrega: '2026-09-08',
        criadorNome: 'Lucas Matheus',
        responsaveis: ['Edyllaine Silva', 'Igor Santos'],
        briefing: 'Gravação de depoimentos de alunos do plano VIP.',
        checklist: [
          { id: 1, descricao: 'Contato com entrevistados', concluido: true, ordem: 1 },
          { id: 2, descricao: 'Gravação no local', concluido: false, ordem: 2 },
        ],
        percentualConcluido: 50,
        dataCriacao: '2026-08-28',
      },
    ];

    const roteiros: Roteiro[] = [
      {
        id: 1,
        titulo: 'NOVO ESPAÇO FITNESS | JM MODA FITNESS',
        loja: 'JM MODA FITNESS',
        criadorNome: 'LUCAS MATHEUS',
        dataGravacao: '2026-08-12',
        status: 'EM_GRAVACAO',
        feito: false,
        conteudoScript: `CENA 1 (00:00 - 00:05): Drone aproximando da fachada nova. Locução marcante: 'Prepare-se para o seu melhor treino'.\n\nCENA 2 (00:05 - 00:15): Planos detalhe dos novos pesos e esteiras de última geração em ritmo acelerado.\n\nCENA 3 (00:15 - 00:25): Atleta executando agachamento livre com iluminação de recorte e névoa suave de fundo.\n\nCENA 4 (00:25 - 00:30): Encerramento com CTA: 'Novo Espaço Fitness. Venha hoje mesmo!' e exibição da Logo.`,
        observacoesSet: 'Lente 24-70mm f/2.8, iluminação em 5600K com bastões RGB azuis. Levar microfone de lapela sem fio.',
        dataCriacao: '2026-08-01',
      },
      {
        id: 2,
        titulo: 'LANÇAMENTO COLEÇÃO PRIMAVERA | ATELIÊ DA YSA',
        loja: 'ATELIÊ DA YSA',
        criadorNome: 'LUCAS MATHEUS',
        dataGravacao: '2026-08-20',
        status: 'PENDENTE',
        feito: false,
        conteudoScript: `CENA 1: Modelo entrando no ateliê em slow-motion admirando os vestidos.\n\nCENA 2: Close-up nos tecidos bordados à mão e detalhes da costura autêntica.\n\nCENA 3: Ysa explicando a inspiração botânica da nova coleção.`,
        observacoesSet: 'Iluminação suave natural de janela difusa com rebatedor dourado.',
        dataCriacao: '2026-08-10',
      },
    ];

    const logos: LogoCliente[] = [
      {
        id: 1,
        clienteNome: 'JM MODA FITNESS',
        variante: 'Logo Principal Colorida',
        formato: 'PNG',
        tamanho: '2.4 MB - 4000x2500px',
        corPrimaria: '#E11D48',
        arquivoUrlOuBase64:
          'https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=400&auto=format&fit=crop&q=60',
        dataUpload: '2026-07-01',
      },
      {
        id: 2,
        clienteNome: 'ATELIÊ DA YSA',
        variante: 'Versão Negativa Branca',
        formato: 'SVG',
        tamanho: '450 KB - Vetor',
        corPrimaria: '#8B5CF6',
        arquivoUrlOuBase64:
          'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=400&auto=format&fit=crop&q=60',
        dataUpload: '2026-07-05',
      },
      {
        id: 3,
        clienteNome: 'ÓTICAS PRIME',
        variante: 'Símbolo & Marca',
        formato: 'PNG',
        tamanho: '1.8 MB - 3000x3000px',
        corPrimaria: '#0EA5E9',
        arquivoUrlOuBase64:
          'https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=400&auto=format&fit=crop&q=60',
        dataUpload: '2026-07-10',
      },
    ];

    const fotosMock: FotoEvento[] = [
      {
        id: 1,
        codigoFoto: 'BR26-001',
        titulo: 'Corrida 10k - Ponto Km 3 #1',
        urlOuBase64: 'https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=600&auto=format&fit=crop&q=60',
        preco: 10.0,
        marcaDaguaTexto: 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
        visualizacoes: 120,
        vendas: 5,
      },
      {
        id: 2,
        codigoFoto: 'BR26-002',
        titulo: 'Corrida 10k - Ponto Km 3 #2',
        urlOuBase64: 'https://images.unsplash.com/photo-1530549387789-4c1017266635?w=600&auto=format&fit=crop&q=60',
        preco: 10.0,
        marcaDaguaTexto: 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
        visualizacoes: 155,
        vendas: 7,
      },
      {
        id: 3,
        codigoFoto: 'BR26-003',
        titulo: 'Corrida 10k - Ponto Km 3 #3',
        urlOuBase64: 'https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=600&auto=format&fit=crop&q=60',
        preco: 10.0,
        marcaDaguaTexto: 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
        visualizacoes: 190,
        vendas: 9,
      },
      {
        id: 4,
        codigoFoto: 'BR26-004',
        titulo: 'Corrida 10k - Ponto Km 3 #4',
        urlOuBase64: 'https://images.unsplash.com/photo-1517649763962-0c623266ddc0?w=600&auto=format&fit=crop&q=60',
        preco: 10.0,
        marcaDaguaTexto: 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
        visualizacoes: 225,
        vendas: 11,
      },
    ];

    const eventos: Evento[] = [
      {
        id: 1,
        nome: 'BARRA RUN 2026',
        localizacao: 'Barra de São Miguel - AL, Brasil',
        dataEvento: '2026-08-01',
        horario: '05:00 às 09:00',
        precoFotoVendida: 10.0,
        publicoEstimado: 500,
        bannerUrl:
          'https://images.unsplash.com/photo-1452626038306-9aae5e071dd3?w=800&auto=format&fit=crop&q=60',
        descricao: 'Maior corrida de rua da Barra de São Miguel com percursos de 5km e 10km na orla marítima.',
        status: 'PUBLICADO',
        fotos: fotosMock,
      },
    ];

    const vendas: VendaFoto[] = [
      {
        id: 1,
        codigoVenda: '#257168767',
        clienteNome: 'Adrianny Evelyn',
        eventoNome: 'BARRA RUN 2026',
        qtdFotos: 2,
        qtdVideos: 0,
        valorTotal: 22.5,
        status: 'PAGO',
        dataDisponivelInfo: 'Disponível em 28/07/2026',
        dataVenda: '2026-07-27T19:42:00',
      },
      {
        id: 2,
        codigoVenda: '#257157586',
        clienteNome: 'Jadiel Soares',
        eventoNome: 'BARRA RUN 2026',
        qtdFotos: 1,
        qtdVideos: 0,
        valorTotal: 9.0,
        status: 'PAGO',
        dataDisponivelInfo: 'Disponível em 28/07/2026',
        dataVenda: '2026-07-27T19:19:00',
      },
      {
        id: 3,
        codigoVenda: '#257146110',
        clienteNome: 'Emanoella Esterfanny',
        eventoNome: 'BARRA RUN 2026',
        qtdFotos: 1,
        qtdVideos: 0,
        valorTotal: 9.0,
        status: 'PAGO',
        dataDisponivelInfo: 'Disponível em 28/07/2026',
        dataVenda: '2026-07-27T15:00:00',
      },
      {
        id: 4,
        codigoVenda: '#257145261',
        clienteNome: 'Michellandy Melo dos Santos',
        eventoNome: 'BARRA RUN 2026',
        qtdFotos: 6,
        qtdVideos: 0,
        valorTotal: 54.0,
        status: 'PAGO',
        dataDisponivelInfo: 'Disponível em 28/07/2026',
        dataVenda: '2026-07-27T14:43:00',
      },
      {
        id: 5,
        codigoVenda: '#257139503',
        clienteNome: 'Jessyka Marques',
        eventoNome: 'BARRA RUN 2026',
        qtdFotos: 1,
        qtdVideos: 0,
        valorTotal: 9.0,
        status: 'PAGO',
        dataDisponivelInfo: 'Disponível em 28/07/2026',
        dataVenda: '2026-07-27T13:03:00',
      },
      {
        id: 6,
        codigoVenda: '#257126789',
        clienteNome: 'Adrielle Santos',
        eventoNome: 'BARRA RUN 2026',
        qtdFotos: 3,
        qtdVideos: 0,
        valorTotal: 27.0,
        status: 'PAGO',
        dataDisponivelInfo: 'Disponível em 28/07/2026',
        dataVenda: '2026-07-27T12:10:00',
      },
      {
        id: 7,
        codigoVenda: '#257114145',
        clienteNome: 'Beatriz Ferreira',
        eventoNome: 'BARRA RUN 2026',
        qtdFotos: 2,
        qtdVideos: 0,
        valorTotal: 18.0,
        status: 'PAGO',
        dataDisponivelInfo: 'Disponível em 28/07/2026',
        dataVenda: '2026-07-27T11:36:00',
      },
      {
        id: 8,
        codigoVenda: '#257033145',
        clienteNome: 'Thacylla Cavalcante',
        eventoNome: 'BARRA RUN 2026',
        qtdFotos: 3,
        qtdVideos: 0,
        valorTotal: 27.0,
        status: 'PENDENTE',
        dataDisponivelInfo: 'Disponível em 29/07/2026',
        dataVenda: '2026-07-27T11:33:00',
      },
      {
        id: 9,
        codigoVenda: '#257023677',
        clienteNome: 'João Pedro Pagrian',
        eventoNome: 'BARRA RUN 2026',
        qtdFotos: 1,
        qtdVideos: 0,
        valorTotal: 13.5,
        status: 'PENDENTE',
        dataDisponivelInfo: 'Disponível em 29/07/2026',
        dataVenda: '2026-07-27T08:55:00',
      },
      {
        id: 10,
        codigoVenda: '#257023604',
        clienteNome: 'Mariana Correia',
        eventoNome: 'BARRA RUN 2026',
        qtdFotos: 1,
        qtdVideos: 0,
        valorTotal: 10.0,
        status: 'ATRASADO',
        dataDisponivelInfo: 'Disponível em 25/07/2026',
        dataVenda: '2026-07-27T08:52:00',
      },
    ];

    const despesas: Despesa[] = [
      {
        id: 1,
        descricao: 'Locação de Lentes Cinema 50mm / 85mm',
        categoria: 'Equipamentos',
        valor: 450.0,
        dataDespesa: '2026-07-22',
        formaPagamento: 'PIX',
        status: 'PAGO',
        observacoes: 'Lentes para gravação da JM Fitness',
      },
      {
        id: 2,
        descricao: 'Combustível e Deslocamento Equipe Barra Run',
        categoria: 'Transporte',
        valor: 280.0,
        dataDespesa: '2026-07-25',
        formaPagamento: 'Cartão de Crédito',
        status: 'PAGO',
      },
      {
        id: 3,
        descricao: 'Assinatura Mensal Adobe Creative Cloud Team',
        categoria: 'Software/Assinaturas',
        valor: 680.0,
        dataDespesa: '2026-07-10',
        formaPagamento: 'Boleto',
        status: 'PAGO',
      },
      {
        id: 4,
        descricao: 'Cachê Fotógrafo Convidado - Barra Run',
        categoria: 'Equipe/Cachês',
        valor: 800.0,
        dataDespesa: '2026-08-01',
        formaPagamento: 'PIX',
        status: 'PAGO',
      },
    ];

    return { tarefas, roteiros, logos, eventos, vendas, despesas };
  }
}
