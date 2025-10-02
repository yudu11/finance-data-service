export interface PriceData {
  symbol: string;
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  source: string;
}

export interface SymbolPriceHistory {
  symbol: string;
  prices: PriceData[];
}
