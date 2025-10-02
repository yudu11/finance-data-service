import { Navigate, Route, Routes } from 'react-router-dom';
import SymbolSelectionPage from './pages/SymbolSelectionPage';
import PriceChartPage from './pages/PriceChartPage';

function App(): JSX.Element {
  return (
    <Routes>
      <Route path="/" element={<SymbolSelectionPage />} />
      <Route path="/chart" element={<PriceChartPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
