import { useQuery } from '@tanstack/react-query';
import { fetchPriceData } from '../api/client';
import type { SymbolPriceHistory } from '../api/types';

async function loadPriceHistory(symbols: string[]): Promise<SymbolPriceHistory[]> {
  const requests = symbols.map(async (symbol) => ({
    symbol,
    prices: await fetchPriceData(symbol)
  }));
  return Promise.all(requests);
}

export function usePriceHistory(symbols: string[]) {
  const normalizedSymbols = [...symbols].map((symbol) => symbol.toUpperCase()).sort();
  return useQuery({
    queryKey: ['price-history', normalizedSymbols],
    queryFn: () => loadPriceHistory(normalizedSymbols),
    enabled: normalizedSymbols.length > 0,
    retry: 1
  });
}
