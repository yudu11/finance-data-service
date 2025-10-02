import { useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import ReactApexChart from 'react-apexcharts';
import type { ApexOptions } from 'apexcharts';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Container,
  Divider,
  Paper,
  Stack,
  Typography
} from '@mui/material';
import { usePriceHistory } from '../hooks/usePriceHistory';
import type { PriceData } from '../api/types';

interface ChartPoint {
  x: string;
  y: number | null;
  meta: PriceData | null;
}

type ChartSeries = { name: string; data: ChartPoint[] };

interface TooltipContext {
  dataPointIndex: number;
  w: {
    config: {
      series: ChartSeries[];
    };
    globals: {
      categoryLabels: string[];
    };
  };
}

const coerceNumber = (value: number | string) => (typeof value === 'number' ? value : Number(value));

function PriceChartPage(): JSX.Element {
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const symbols = useMemo(
    () =>
      (params.get('symbols') ?? '')
        .split(',')
        .map((symbol) => symbol.trim().toUpperCase())
        .filter((symbol) => symbol.length > 0),
    [params]
  );

  const startDate = params.get('start') ?? undefined;
  const endDate = params.get('end') ?? undefined;

  const { data, isLoading, isError, error } = usePriceHistory(symbols);

  const filteredData = useMemo(() => {
    if (!data) {
      return [] as { symbol: string; prices: PriceData[] }[];
    }

    const startMs = startDate ? new Date(startDate).getTime() : Number.NEGATIVE_INFINITY;
    const endMs = endDate ? new Date(endDate).getTime() : Number.POSITIVE_INFINITY;

    return data.map(({ symbol, prices }) => ({
      symbol,
      prices: prices
        .filter((price) => {
          const priceTime = new Date(price.date).getTime();
          return priceTime >= startMs && priceTime <= endMs;
        })
        .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
    }));
  }, [data, startDate, endDate]);

  const uniqueDates = useMemo<string[]>(() => {
    const dateSet = new Set<string>();
    filteredData.forEach(({ prices }) => {
      prices.forEach((price) => dateSet.add(price.date));
    });
    return Array.from(dateSet).sort((a, b) => new Date(a).getTime() - new Date(b).getTime());
  }, [filteredData]);

  const series = useMemo<ChartSeries[]>(() => {
    return filteredData.map(({ symbol, prices }) => {
      const priceMap = new Map<string, PriceData>(prices.map((price) => [price.date, price] as const));
      const dataPoints: ChartPoint[] = uniqueDates.map((date) => {
        const price = priceMap.get(date);
        return {
          x: date,
          y: price ? coerceNumber(price.close) : null,
          meta: price ?? null
        };
      });
      return {
        name: symbol,
        data: dataPoints
      };
    });
  }, [filteredData, uniqueDates]);

  const hasData = series.some((serie) => serie.data.some((point) => point.y !== null));

  const chartOptions: ApexOptions = useMemo(() => ({
    chart: {
      type: 'line',
      height: 520,
      zoom: {
        type: 'x',
        enabled: true
      },
      toolbar: {
        show: true,
        tools: {
          download: true,
          selection: true,
          zoom: true,
          zoomin: true,
          zoomout: true,
          pan: true,
          reset: true
        }
      }
    },
    stroke: {
      curve: 'smooth',
      width: 2
    },
    markers: {
      size: 0
    },
    xaxis: {
      type: 'datetime',
      labels: {
        datetimeUTC: false
      }
    },
    yaxis: {
      labels: {
        formatter: (value) => value.toFixed(2)
      }
    },
    tooltip: {
      shared: true,
      intersect: false,
      x: {
        format: 'yyyy-MM-dd'
      },
      custom({ dataPointIndex, w }: TooltipContext) {
        const dateLabel = w.globals.categoryLabels[dataPointIndex];
        const rows = w.config.series
          .map((serie) => {
            const point = serie.data[dataPointIndex];
            if (!point?.meta) {
              return '';
            }
            const { meta } = point;
            return `<div class="tooltip-row"><span class="symbol">${serie.name}</span><span class="values">O ${coerceNumber(
              meta.open
            ).toFixed(2)} · H ${coerceNumber(meta.high).toFixed(2)} · L ${coerceNumber(meta.low).toFixed(2)} · C ${coerceNumber(
              meta.close
            ).toFixed(2)}</span></div>`;
          })
          .join('');

        return `<div class="chart-tooltip"><div class="date">${dateLabel}</div>${rows}</div>`;
      }
    },
    dataLabels: {
      enabled: false
    },
    legend: {
      position: 'top',
      horizontalAlign: 'left'
    }
  }), []);

  if (!symbols.length) {
    return (
      <Container maxWidth="md" sx={{ py: 6 }}>
        <Paper sx={{ p: 4 }}>
          <Stack spacing={3}>
            <Typography variant="h5">No symbols selected</Typography>
            <Typography>Select one or more symbols to view their historical prices.</Typography>
            <Button variant="contained" onClick={() => navigate('/')}>Return to selection</Button>
          </Stack>
        </Paper>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', md: 'center' }} spacing={2}>
          <Box>
            <Typography variant="h4" component="h1">
              Price history
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {symbols.join(', ')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {startDate ? `From ${startDate}` : 'From earliest data'} {endDate ? `to ${endDate}` : 'to latest data'}
            </Typography>
          </Box>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <Button variant="outlined" onClick={() => navigate('/')}>Change selection</Button>
          </Stack>
        </Stack>

        <Paper sx={{ p: { xs: 2, md: 3 } }}>
          {isLoading ? (
            <Stack alignItems="center" justifyContent="center" sx={{ minHeight: 360 }}>
              <CircularProgress />
            </Stack>
          ) : isError ? (
            <Alert severity="error">
              Failed to load price data. {error instanceof Error ? error.message : ''}
            </Alert>
          ) : !hasData ? (
            <Alert severity="info">No price data available for the selected configuration.</Alert>
          ) : (
            <Box>
              <ReactApexChart
                options={chartOptions}
                series={series as unknown as ApexOptions['series']}
                type="line"
                height={520}
              />
            </Box>
          )}
        </Paper>

        <Paper sx={{ p: { xs: 2, md: 3 } }}>
          <Typography variant="h6" gutterBottom>
            How to read the chart
          </Typography>
          <Divider sx={{ my: 1 }} />
          <Typography variant="body2" color="text.secondary">
            Use the toolbar to zoom into a specific time range or pan across the series. Hover the chart to compare
            the open, high, low, and close prices for every selected symbol on a given date.
          </Typography>
        </Paper>
      </Stack>
    </Container>
  );
}

export default PriceChartPage;
