import source from '../../javafro.yaml?raw';
import { parseConfig } from './config-schema';

// Vite validates the same file before starting/building; the bundle works offline.
export const config = parseConfig(source);
