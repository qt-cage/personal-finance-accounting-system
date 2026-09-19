import { use } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent, GraphicComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, GraphicComponent, TitleComponent, CanvasRenderer])

export { init, graphic } from 'echarts/core'
