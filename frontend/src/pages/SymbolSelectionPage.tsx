import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Container,
  Paper,
  Stack,
  Typography
} from '@mui/material';
import SymbolSelector from '../components/SymbolSelector';
import DateRangeSelector from '../components/DateRangeSelector';
import { useSymbols } from '../hooks/useSymbols';

const toDateInputValue = (date: Date) => date.toISOString().slice(0, 10);

const defaultEndDate = toDateInputValue(new Date());
const defaultStartDate = toDateInputValue(new Date(Date.now() - 1000 * 60 * 60 * 24 * 180));

function SymbolSelectionPage(): JSX.Element {
  const navigate = useNavigate();
  const { data: symbolOptions = [], isLoading, isError, error } = useSymbols();
  const [selectedSymbols, setSelectedSymbols] = useState<string[]>([]);
  const [dateRange, setDateRange] = useState<{ startDate?: string; endDate?: string }>({
    startDate: defaultStartDate,
    endDate: defaultEndDate
  });
  const [formError, setFormError] = useState<string | null>(null);

  const helperText = useMemo(() => {
    if (formError) {
      return formError;
    }
    if (!selectedSymbols.length) {
      return 'Choose at least one symbol to continue';
    }
    return undefined;
  }, [formError, selectedSymbols.length]);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError(null);

    if (!selectedSymbols.length) {
      setFormError('Select at least one symbol before continuing');
      return;
    }

    if (
      dateRange.startDate &&
      dateRange.endDate &&
      dateRange.startDate > dateRange.endDate
    ) {
      setFormError('Start date must be before end date');
      return;
    }

    const params = new URLSearchParams();
    params.set('symbols', selectedSymbols.join(','));
    if (dateRange.startDate) {
      params.set('start', dateRange.startDate);
    }
    if (dateRange.endDate) {
      params.set('end', dateRange.endDate);
    }

    navigate(`/chart?${params.toString()}`);
  };

  return (
    <Container maxWidth="md" sx={{ py: 6 }}>
      <Paper elevation={3} sx={{ p: { xs: 3, sm: 4 } }} component="form" onSubmit={handleSubmit}>
        <Stack spacing={4}>
          <Box>
            <Typography variant="h4" component="h1" gutterBottom>
              Finance Data Explorer
            </Typography>
            <Typography variant="body1" color="text.secondary">
              Select one or more symbols to visualize their historical price trends. Gold (XAUUSD)
              is available alongside equities to help you compare performance.
            </Typography>
          </Box>

          {isError ? (
            <Alert severity="error">
              Failed to load available symbols. {error instanceof Error ? error.message : ''}
            </Alert>
          ) : null}

          <SymbolSelector
            options={symbolOptions}
            value={selectedSymbols}
            loading={isLoading}
            onChange={setSelectedSymbols}
            error={Boolean(formError)}
            helperText={helperText}
          />

          <DateRangeSelector
            startDate={dateRange.startDate}
            endDate={dateRange.endDate}
            onChange={setDateRange}
          />

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="flex-end">
            <Button
              type="button"
              variant="outlined"
              onClick={() => {
                setSelectedSymbols([]);
                setDateRange({ startDate: defaultStartDate, endDate: defaultEndDate });
                setFormError(null);
              }}
            >
              Reset
            </Button>
            <Button type="submit" variant="contained" size="large" disabled={isLoading}>
              Display Chart
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Container>
  );
}

export default SymbolSelectionPage;
