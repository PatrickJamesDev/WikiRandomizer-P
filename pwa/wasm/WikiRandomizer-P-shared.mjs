

import { instantiate } from './WikiRandomizer-P-shared.uninstantiated.mjs';


const exports = (await instantiate({

})).exports;

export const {
articleUrl,
mobileArticleUrl,
summaryUrl,
randomSummaryUrl,
searchUrl,
filePathUrl,
normalizeTitle,
encodeTitle,
plainText,
snippet,
readingMinutes,
memory,
_initialize
} = exports


