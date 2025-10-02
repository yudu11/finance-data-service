import { useQuery } from '@tanstack/react-query';
import { fetchSymbols } from '../api/client';

export function useSymbols() {
  return useQuery({
    queryKey: ['symbols'],
    queryFn: fetchSymbols,
    staleTime: 1000 * 60 * 10,
    retry: 1
  });
}
