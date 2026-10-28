import { use } from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent, GraphicComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, PieChart, BarChart, GridComponent, LegendComponent, TooltipComponent, GraphicComponent, TitleComponent, CanvasRenderer])

export { init, graphic } from 'echarts/core'
