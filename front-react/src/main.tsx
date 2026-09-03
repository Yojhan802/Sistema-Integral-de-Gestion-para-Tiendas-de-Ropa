import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '../../front/css/fonts.css';
import '../../front/css/tokens.css';
import '../../front/css/reset.css';
import '../../front/css/base.css';
import '../../front/css/layout.css';
import '../../front/css/components.css';
import '../../front/css/pages.css';
import '../../front/css/responsive.css';
import './templates/storefront-base.css';
import './react-ui.css';
import './templates/index.css';
import { App } from './App';

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
